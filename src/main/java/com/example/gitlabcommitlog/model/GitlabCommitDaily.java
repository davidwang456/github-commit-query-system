package com.example.gitlabcommitlog.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "gitlab_commit_daily")
public class GitlabCommitDaily {
    @Id
    private String id;
    private String token;
    private String date;
    private int count;

    public GitlabCommitDaily() {
    }

    public GitlabCommitDaily(String date, int count, String token) {
        this.date = date;
        this.count = count;
        this.token = token;
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
