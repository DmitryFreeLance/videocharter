package com.videocharter.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiterService {

    private final Duration minimumInterval;
    private final Map<Long, Instant> lastActionByUser = new ConcurrentHashMap<>();

    public RateLimiterService(Duration minimumInterval) {
        this.minimumInterval = minimumInterval;
    }

    public boolean isTooFast(long userId) {
        Instant now = Instant.now();
        Instant previous = lastActionByUser.put(userId, now);
        return previous != null && Duration.between(previous, now).compareTo(minimumInterval) < 0;
    }
}
