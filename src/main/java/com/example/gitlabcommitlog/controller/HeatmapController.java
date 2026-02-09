package com.example.gitlabcommitlog.controller;

import com.example.gitlabcommitlog.model.CommitDaily;
import com.example.gitlabcommitlog.service.GithubSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HeatmapController {
    private static final Logger logger = LoggerFactory.getLogger(HeatmapController.class);
    private final GithubSyncService syncService;

    public HeatmapController(GithubSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetch(
            @RequestHeader(value = "X-Github-Token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {
        Map<String, Object> response = new HashMap<>();
        String token = resolveToken(tokenHeader, tokenParam);
        LocalDate end = LocalDate.now(ZoneId.systemDefault());
        LocalDate start = end.minusYears(1).plusDays(1);

        logger.info("Fetch request, token={}", maskToken(token));

        if (syncService.hasTokenData(token)) {
            List<CommitDaily> dailyCounts = syncService.getDailyCounts(start, end, token);
            response.put("status", "cached");
            response.put("days", dailyCounts.size());
            response.put("data", dailyCounts);
            logger.info("Cache hit, token={}, returning commit_daily size={}", maskToken(token), dailyCounts.size());
            return ResponseEntity.ok(response);
        }

        syncService.syncLastYear(token);
        List<CommitDaily> dailyCounts = syncService.getDailyCounts(start, end, token);
        response.put("days", dailyCounts.size());
        response.put("status", "synced");
        response.put("data", dailyCounts);
        logger.info("Sync completed, token={}, new days={}", maskToken(token), dailyCounts.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncLatest(
            @RequestHeader(value = "X-Github-Token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestParam(value = "range", defaultValue = "week") String range) {
        Map<String, Object> response = new HashMap<>();
        String token = resolveToken(tokenHeader, tokenParam);
        LocalDate end = LocalDate.now(ZoneId.systemDefault());
        LocalDate start = end.minusYears(1).plusDays(1);

        logger.info("Sync Latest: token={}, range={} (only this range is synced from GitHub)", maskToken(token), range);

        syncService.syncRecent(token, range);
        List<CommitDaily> dailyCounts = syncService.getDailyCounts(start, end, token);
        response.put("days", dailyCounts.size());
        response.put("status", "synced");
        response.put("data", dailyCounts);
        logger.info("Sync Latest done: token={}, range={}, heatmapDays={}", maskToken(token), range, dailyCounts.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/heatmap")
    public ResponseEntity<List<CommitDaily>> heatmap(
            @RequestHeader(value = "X-Github-Token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {
        LocalDate end = LocalDate.now(ZoneId.systemDefault());
        LocalDate start = end.minusYears(1).plusDays(1);
        String token = resolveToken(tokenHeader, tokenParam);
        logger.info("Fetching heatmap data, token={}, range={} ~ {}", maskToken(token), start, end);
        return ResponseEntity.ok(syncService.getDailyCounts(start, end, token));
    }

    @GetMapping("/commits")
    public ResponseEntity<Map<String, Object>> commits(
            @RequestHeader(value = "X-Github-Token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String branch,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String token = resolveToken(tokenHeader, tokenParam);
        logger.info("Querying commit records, token={}, project={}, branch={}, page={}, size={}",
                maskToken(token), project, branch, page, size);
        return ResponseEntity.ok(syncService.queryCommitRecords(project, branch, page, size, token));
    }

    @GetMapping("/projects")
    public ResponseEntity<List<String>> projects(
            @RequestHeader(value = "X-Github-Token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {
        String token = resolveToken(tokenHeader, tokenParam);
        logger.info("Fetching project list, token={}", maskToken(token));
        return ResponseEntity.ok(syncService.getAllProjects(token));
    }

    @GetMapping("/branches")
    public ResponseEntity<List<String>> branches(
            @RequestHeader(value = "X-Github-Token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestParam String project) {
        String token = resolveToken(tokenHeader, tokenParam);
        logger.info("Fetching branch list, token={}, project={}", maskToken(token), project);
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
