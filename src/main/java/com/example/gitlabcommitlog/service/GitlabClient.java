package com.example.gitlabcommitlog.service;

import com.example.gitlabcommitlog.config.GithubProperties;
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
public class GitlabClient {
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;
    private final GithubProperties properties;

    public GitlabClient(RestTemplate restTemplate, GithubProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public List<Map<String, Object>> fetchAllProjects(String token) {
        String endpoint = properties.getBaseUrl() + "/user/repos";
        List<Map<String, Object>> results = new ArrayList<>();
        int page = 1;

        while (true) {
            URI uri = UriComponentsBuilder.fromHttpUrl(endpoint)
                    .queryParam("visibility", "all")
                    .queryParam("affiliation", "owner,collaborator,organization_member")
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
            page += 1;
        }

        return results;
    }

    public Map<String, Integer> fetchLanguages(String fullName, String token) {
        String endpoint = properties.getBaseUrl() + "/repos/" + fullName + "/languages";
        ResponseEntity<Map<String, Integer>> response = restTemplate.exchange(
                endpoint, HttpMethod.GET, new HttpEntity<>(buildHeaders(token)),
                new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public List<String> fetchBranches(String fullName, String token) {
        String endpoint = properties.getBaseUrl() + "/repos/" + fullName + "/branches";
        List<String> results = new ArrayList<>();
        int page = 1;

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
            page += 1;
        }

        return results;
    }

    public List<Map<String, Object>> fetchCommits(String fullName, String branch, OffsetDateTime since, OffsetDateTime until,
                                                  String token) {
        String endpoint = properties.getBaseUrl() + "/repos/" + fullName + "/commits";
        List<Map<String, Object>> results = new ArrayList<>();
        int page = 1;

        while (true) {
            URI uri = UriComponentsBuilder.fromHttpUrl(endpoint)
                    .queryParam("since", since.toString())
                    .queryParam("until", until.toString())
                    .queryParam("sha", branch)
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
            page += 1;
        }

        return results;
    }

    private HttpHeaders buildHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isBlank()) {
            headers.set("Authorization", "token " + token);
        }
        return headers;
    }
}
