package com.example.gitlabcommitlog.service;

import com.example.gitlabcommitlog.config.GitlabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GitlabApiClient {
    private static final Logger logger = LoggerFactory.getLogger(GitlabApiClient.class);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final GitlabProperties properties;

    public GitlabApiClient(RestTemplate restTemplate, GitlabProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public List<Map<String, Object>> fetchAllProjects(String token) {
        String endpoint = properties.getBaseUrl() + "/projects";
        List<Map<String, Object>> results = new ArrayList<>();
        int page = 1;

        logger.info("Start fetching GitLab projects");
        while (true) {
            URI uri = UriComponentsBuilder.fromHttpUrl(endpoint)
                    .queryParam("membership", true)
                    .queryParam("per_page", 100)
                    .queryParam("page", page)
                    .build()
                    .toUri();

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(buildHeaders(token)), LIST_MAP_TYPE);

            List<Map<String, Object>> body = response.getBody();
            if (body == null || body.isEmpty()) {
                break;
            }
            results.addAll(body);
            logger.info("Fetched GitLab projects page {}, count={}", page, body.size());
            page += 1;
        }

        logger.info("Finished fetching GitLab projects, total={}", results.size());
        return results;
    }

    public Map<String, Double> fetchLanguages(long projectId, String token) {
        String endpoint = properties.getBaseUrl() + "/projects/" + projectId + "/languages";
        logger.info("Fetching GitLab project languages: {}", projectId);
        ResponseEntity<Map<String, Double>> response = restTemplate.exchange(
                endpoint, HttpMethod.GET, new HttpEntity<>(buildHeaders(token)),
                new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public List<String> fetchBranches(long projectId, String token) {
        String endpoint = properties.getBaseUrl() + "/projects/" + projectId + "/repository/branches";
        List<String> results = new ArrayList<>();
        int page = 1;

        logger.info("Start fetching GitLab branches: {}", projectId);
        while (true) {
            URI uri = UriComponentsBuilder.fromHttpUrl(endpoint)
                    .queryParam("per_page", 100)
                    .queryParam("page", page)
                    .build()
                    .toUri();

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(buildHeaders(token)), LIST_MAP_TYPE);

            List<Map<String, Object>> body = response.getBody();
            if (body == null || body.isEmpty()) {
                break;
            }
            for (Map<String, Object> branch : body) {
                Object name = branch.get("name");
                if (name != null) {
                    results.add(name.toString());
                }
            }
            logger.info("Fetched GitLab branches page {}, count={}", page, body.size());
            page += 1;
        }

        logger.info("Finished fetching GitLab branches: {}, total={}", projectId, results.size());
        return results;
    }

    public List<Map<String, Object>> fetchCommits(long projectId, String branch, OffsetDateTime since,
                                                  OffsetDateTime until, String token) {
        String endpoint = properties.getBaseUrl() + "/projects/" + projectId + "/repository/commits";
        List<Map<String, Object>> results = new ArrayList<>();
        int page = 1;

        logger.info("Start fetching GitLab commits: {}, branch={}", projectId, branch);
        while (true) {
            URI uri = UriComponentsBuilder.fromHttpUrl(endpoint)
                    .queryParam("since", since.toString())
                    .queryParam("until", until.toString())
                    .queryParam("ref_name", branch)
                    .queryParam("per_page", 100)
                    .queryParam("page", page)
                    .build()
                    .toUri();

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(buildHeaders(token)), LIST_MAP_TYPE);

            List<Map<String, Object>> body = response.getBody();
            if (body == null || body.isEmpty()) {
                break;
            }
            results.addAll(body);
            logger.info("Fetched GitLab commits page {}, count={}", page, body.size());
            page += 1;
        }

        logger.info("Finished fetching GitLab commits: {}, branch={}, total={}", projectId, branch, results.size());
        return results;
    }

    private HttpHeaders buildHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isBlank()) {
            headers.set("Private-Token", token);
        }
        return headers;
    }
}
