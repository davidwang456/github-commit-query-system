package com.example.gitlabcommitlog.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, GithubProperties properties) {
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            HttpHeaders headers = request.getHeaders();
            if (properties.getToken() != null && !properties.getToken().isEmpty()) {
                headers.set("Authorization", "token " + properties.getToken());
            }
            headers.set("Accept", "application/vnd.github+json");
            headers.set("User-Agent", "gitlab-commit-log");
            return execution.execute(request, body);
        };

        return builder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(Duration.ofSeconds(60))
                .additionalInterceptors(authInterceptor)
                .build();
    }
}
