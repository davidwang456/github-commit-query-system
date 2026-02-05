package com.example.gitlabcommitlog.service;

import com.example.gitlabcommitlog.model.CommitDaily;
import com.example.gitlabcommitlog.model.CommitRecord;
import com.example.gitlabcommitlog.model.ProjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class GitlabSyncService {
    private static final Logger logger = LoggerFactory.getLogger(GitlabSyncService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final GitlabClient client;
    private final MongoTemplate mongoTemplate;

    public GitlabSyncService(GitlabClient client, MongoTemplate mongoTemplate) {
        this.client = client;
        this.mongoTemplate = mongoTemplate;
    }

    public Map<LocalDate, Integer> syncLastYear(String token) {
        LocalDate end = LocalDate.now(ZoneId.systemDefault());
        LocalDate start = end.minusYears(1).plusDays(1);
        return syncRange(start, end, token);
    }

    public Map<LocalDate, Integer> syncRange(LocalDate start, LocalDate end, String token) {
        if (token == null || token.isBlank()) {
            return Map.of();
        }
        String maskedToken = maskToken(token);
        OffsetDateTime since = start.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime until = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();

        Map<LocalDate, Integer> dailyCounts = new HashMap<>();

        logger.info("Start syncing commits, token={}, range={} ~ {}", maskedToken, start, end);
        List<Map<String, Object>> projects = client.fetchAllProjects(token);
        logger.info("Projects to process: {}", projects.size());
        for (Map<String, Object> project : projects) {
            Long projectId = ((Number) project.get("id")).longValue();
            String fullName = (String) project.get("full_name");
            String projectName = fullName != null ? fullName : (String) project.get("name");
            boolean isPrivate = Boolean.TRUE.equals(project.get("private"));
            String visibility = isPrivate ? "private" : "public";

            if (fullName == null || fullName.isBlank()) {
                continue;
            }

            Map<String, Integer> languages = client.fetchLanguages(fullName, token);
            String topLanguage = resolveTopLanguage(languages);
            String projectKey = token + ":" + projectId;
            mongoTemplate.save(new ProjectInfo(projectKey, token, projectName, visibility, topLanguage));

            List<String> branches = client.fetchBranches(fullName, token);
            Set<String> seenShas = new HashSet<>();
            for (String branch : branches) {
                List<Map<String, Object>> commits = client.fetchCommits(fullName, branch, since, until, token);
                for (Map<String, Object> commit : commits) {
                    Object shaObj = commit.get("sha");
                    if (shaObj == null) {
                        continue;
                    }
                    String sha = shaObj.toString();
                    if (!seenShas.add(sha)) {
                        continue;
                    }
                    String committedDate = extractCommitDate(commit);
                    if (committedDate == null) {
                        continue;
                    }
                    LocalDate date = OffsetDateTime.parse(committedDate).toLocalDate();
                    if (date.isBefore(start) || date.isAfter(end)) {
                        continue;
                    }
                    CommitRecord record = buildCommitRecord(fullName, branch, commit, committedDate, token);
                    if (record != null) {
                        mongoTemplate.save(record);
                    }
                    dailyCounts.merge(date, 1, Integer::sum);
                }
            }
            logger.info("Finished project: {}, branches={}, unique commits={}", fullName, branches.size(), seenShas.size());
        }

        upsertDailyCounts(start, end, dailyCounts, token);
        logger.info("Sync finished, token={}, total days={}", maskedToken, dailyCounts.size());
        return dailyCounts;
    }

    public boolean hasTokenData(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Query query = new Query(Criteria.where("token").is(token));
        boolean exists = mongoTemplate.count(query, CommitRecord.class) > 0;
        logger.info("Check cached data, token={}, exists={}", maskToken(token), exists);
        return exists;
    }

    public List<CommitDaily> getDailyCounts(LocalDate start, LocalDate end, String token) {
        if (token == null || token.isBlank()) {
            return List.of();
        }
        Query query = new Query(Criteria.where("date").gte(start.format(DATE_FORMATTER))
                .lte(end.format(DATE_FORMATTER))
                .and("token").is(token));
        List<CommitDaily> existing = mongoTemplate.find(query, CommitDaily.class);
        Map<String, Integer> countMap = new HashMap<>();
        for (CommitDaily daily : existing) {
            countMap.put(daily.getDate(), daily.getCount());
        }

        List<CommitDaily> results = new java.util.ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            String dateStr = cursor.format(DATE_FORMATTER);
            int count = countMap.getOrDefault(dateStr, 0);
            results.add(new CommitDaily(dateStr, count, token));
            cursor = cursor.plusDays(1);
        }
        return results;
    }

    public Map<String, Object> queryCommitRecords(String project, String branch, int page, int size, String token) {
        if (token == null || token.isBlank()) {
            return Map.of("total", 0, "page", Math.max(page, 1), "size", Math.max(size, 1), "records", List.of());
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(Criteria.where("token").is(token));
        if (project != null && !project.isBlank()) {
            criteria.add(Criteria.where("repository").regex(buildContainsRegex(project)));
        }
        if (branch != null && !branch.isBlank()) {
            criteria.add(Criteria.where("branch").regex(buildContainsRegex(branch)));
        }
        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, CommitRecord.class);
        query.skip((long) (safePage - 1) * safeSize);
        query.limit(safeSize);
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "committedAt"));
        List<CommitRecord> records = mongoTemplate.find(query, CommitRecord.class);

        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        response.put("page", safePage);
        response.put("size", safeSize);
        response.put("records", records);
        logger.info("Commit records query finished, token={}, project={}, branch={}, total={}",
                maskToken(token), project, branch, total);
        return response;
    }

    public List<String> getAllProjects(String token) {
        if (token == null || token.isBlank()) {
            return List.of();
        }
        List<String> projects = mongoTemplate.query(CommitRecord.class)
                .distinct("repository")
                .matching(new Query(Criteria.where("token").is(token)))
                .as(String.class)
                .all();
        projects.sort(String::compareToIgnoreCase);
        logger.info("Project list fetched, token={}, count={}", maskToken(token), projects.size());
        return projects;
    }

    public List<String> getBranchesByProject(String project, String token) {
        if (project == null || project.isBlank() || token == null || token.isBlank()) {
            return List.of();
        }
        Query query = new Query(Criteria.where("repository").is(project).and("token").is(token));
        List<String> branches = mongoTemplate.query(CommitRecord.class)
                .distinct("branch")
                .matching(query)
                .as(String.class)
                .all();
        branches.sort(String::compareToIgnoreCase);
        logger.info("Branch list fetched, token={}, project={}, count={}",
                maskToken(token), project, branches.size());
        return branches;
    }

    private void upsertDailyCounts(LocalDate start, LocalDate end, Map<LocalDate, Integer> dailyCounts, String token) {
        Query deleteQuery = new Query(Criteria.where("date")
                .gte(start.format(DATE_FORMATTER))
                .lte(end.format(DATE_FORMATTER))
                .and("token").is(token));
        mongoTemplate.remove(deleteQuery, CommitDaily.class);

        List<CommitDaily> docs = dailyCounts.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> new CommitDaily(entry.getKey().format(DATE_FORMATTER), entry.getValue(), token))
                .toList();
        if (!docs.isEmpty()) {
            mongoTemplate.insertAll(docs);
        }
    }

    private String resolveTopLanguage(Map<String, ? extends Number> languages) {
        if (languages == null || languages.isEmpty()) {
            return null;
        }
        return languages.entrySet().stream()
                .max((left, right) -> Double.compare(left.getValue().doubleValue(), right.getValue().doubleValue()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String extractCommitDate(Map<String, Object> commit) {
        Object commitObj = commit.get("commit");
        if (!(commitObj instanceof Map<?, ?> commitMap)) {
            return null;
        }
        Object committerObj = commitMap.get("committer");
        if (committerObj instanceof Map<?, ?> committerMap) {
            Object date = committerMap.get("date");
            if (date != null) {
                return date.toString();
            }
        }
        Object authorObj = commitMap.get("author");
        if (authorObj instanceof Map<?, ?> authorMap) {
            Object date = authorMap.get("date");
            if (date != null) {
                return date.toString();
            }
        }
        return null;
    }

    private CommitRecord buildCommitRecord(String fullName, String branch, Map<String, Object> commit,
                                           String committedDate, String token) {
        Object shaObj = commit.get("sha");
        if (shaObj == null) {
            return null;
        }
        String sha = shaObj.toString();
        String id = token + ":" + fullName + ":" + sha;
        String message = extractCommitMessage(commit);
        String author = extractCommitAuthor(commit);
        String url = extractCommitUrl(commit);
        return new CommitRecord(id, token, sha, fullName, branch, committedDate, author, message, url);
    }

    private String extractCommitMessage(Map<String, Object> commit) {
        Object commitObj = commit.get("commit");
        if (commitObj instanceof Map<?, ?> commitMap) {
            Object message = commitMap.get("message");
            if (message != null) {
                return message.toString();
            }
        }
        return null;
    }

    private String extractCommitAuthor(Map<String, Object> commit) {
        Object commitObj = commit.get("commit");
        if (commitObj instanceof Map<?, ?> commitMap) {
            Object authorObj = commitMap.get("author");
            if (authorObj instanceof Map<?, ?> authorMap) {
                Object name = authorMap.get("name");
                if (name != null) {
                    return name.toString();
                }
            }
            Object committerObj = commitMap.get("committer");
            if (committerObj instanceof Map<?, ?> committerMap) {
                Object name = committerMap.get("name");
                if (name != null) {
                    return name.toString();
                }
            }
        }
        return null;
    }

    private String extractCommitUrl(Map<String, Object> commit) {
        Object url = commit.get("html_url");
        return url == null ? null : url.toString();
    }

    private Pattern buildContainsRegex(String input) {
        String escaped = Pattern.quote(input.trim());
        return Pattern.compile(".*" + escaped + ".*", Pattern.CASE_INSENSITIVE);
    }

    private String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "empty";
        }
        int length = token.length();
        if (length <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(length - 4);
    }
}
