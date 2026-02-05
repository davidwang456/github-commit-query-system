package com.example.gitlabcommitlog.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "commit_records")
public class CommitRecord {
    @Id
    private String id;
    private String token;
    private String sha;
    private String repository;
    private String branch;
    private String committedAt;
    private String author;
    private String message;
    private String url;

    public CommitRecord() {
    }

    public CommitRecord(String id, String token, String sha, String repository, String branch, String committedAt,
                        String author, String message, String url) {
        this.id = id;
        this.token = token;
        this.sha = sha;
        this.repository = repository;
        this.branch = branch;
        this.committedAt = committedAt;
        this.author = author;
        this.message = message;
        this.url = url;
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

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getCommittedAt() {
        return committedAt;
    }

    public void setCommittedAt(String committedAt) {
        this.committedAt = committedAt;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
