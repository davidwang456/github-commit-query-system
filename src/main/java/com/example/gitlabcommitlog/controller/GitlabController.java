package com.example.gitlabcommitlog.controller;

import com.example.gitlabcommitlog.model.GitlabCommitDaily;
import com.example.gitlabcommitlog.service.GitlabDataSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gitlab")
public class GitlabController {
    private static final Logger logger = LoggerFactory.getLogger(GitlabController.class);
    private final GitlabDataSyncService syncService;

    public GitlabController(GitlabDataSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetch(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {
        Map<String, Object> response = new HashMap<>();
        String token = resolveToken(tokenHeader, tokenParam);
        logger.info("Received GitLab sync request, token={}", maskToken(token));
        if (syncService.hasTokenData(token)) {
            response.put("status", "cached");
            response.put("days", 0);
            logger.info("GitLab cache hit, token={}", maskToken(token));
            return ResponseEntity.ok(response);
        }
        Map<LocalDate, Integer> data = syncService.syncLastYear(token);
        response.put("days", data.size());
        response.put("status", "synced");
        logger.info("GitLab sync completed, token={}, new days={}", maskToken(token), data.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/heatmap")
    public ResponseEntity<List<GitlabCommitDaily>> heatmap(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {
        LocalDate end = LocalDate.now(ZoneId.systemDefault());
        LocalDate start = end.minusYears(1).plusDays(1);
        String token = resolveToken(tokenHeader, tokenParam);
        logger.info("Fetching GitLab heatmap data, token={}, range={} ~ {}", maskToken(token), start, end);
        return ResponseEntity.ok(syncService.getDailyCounts(start, end, token));
    }

    @GetMapping("/commits")
    public ResponseEntity<Map<String, Object>> commits(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String branch,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String token = resolveToken(tokenHeader, tokenParam);
        logger.info("Querying GitLab commits, token={}, project={}, branch={}, page={}, size={}",
                maskToken(token), project, branch, page, size);
        return ResponseEntity.ok(syncService.queryCommitRecords(project, branch, page, size, token));
    }

    @GetMapping("/projects")
    public ResponseEntity<List<String>> projects(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {
        String token = resolveToken(tokenHeader, tokenParam);
        logger.info("Fetching GitLab project list, token={}", maskToken(token));
        return ResponseEntity.ok(syncService.getAllProjects(token));
    }

    @GetMapping("/branches")
    public ResponseEntity<List<String>> branches(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestParam String project) {
        String token = resolveToken(tokenHeader, tokenParam);
        logger.info("Fetching GitLab branches, token={}, project={}", maskToken(token), project);
        return ResponseEntity.ok(syncService.getBranchesByProject(project, token));
    }

    private String resolveToken(String tokenHeader, String tokenParam) {
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            return tokenHeader;
        }
        return tokenParam;
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
