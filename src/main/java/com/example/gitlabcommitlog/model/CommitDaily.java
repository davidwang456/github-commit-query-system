package com.example.gitlabcommitlog.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "commit_daily")
public class CommitDaily {
    @Id
    private String id;
    private String token;
    private String date;
    private int count;

    public CommitDaily() {
    }

    public CommitDaily(String date, int count, String token) {
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
