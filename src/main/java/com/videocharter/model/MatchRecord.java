package com.videocharter.model;

import java.time.Instant;

public class MatchRecord {

    private long firstUserId;
    private long secondUserId;
    private Instant createdAt;

    public MatchRecord() {
    }

    public MatchRecord(long firstUserId, long secondUserId, Instant createdAt) {
        this.firstUserId = firstUserId;
        this.secondUserId = secondUserId;
        this.createdAt = createdAt;
    }

    public long getFirstUserId() {
        return firstUserId;
    }

    public void setFirstUserId(long firstUserId) {
        this.firstUserId = firstUserId;
    }

    public long getSecondUserId() {
        return secondUserId;
    }

    public void setSecondUserId(long secondUserId) {
        this.secondUserId = secondUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
