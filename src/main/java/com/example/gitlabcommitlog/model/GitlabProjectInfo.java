package com.example.gitlabcommitlog.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "gitlab_projects")
public class GitlabProjectInfo {
    @Id
    private String id;
    private String token;
    private String name;
    private String visibility;
    private String language;

    public GitlabProjectInfo() {
    }

    public GitlabProjectInfo(String id, String token, String name, String visibility, String language) {
        this.id = id;
        this.token = token;
        this.name = name;
        this.visibility = visibility;
        this.language = language;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
