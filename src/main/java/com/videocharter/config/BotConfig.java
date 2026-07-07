package com.videocharter.config;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record BotConfig(
        String token,
        String username,
        Path dataFile,
        Set<Long> adminIds,
        long minimumActionIntervalMs
) {

    public static BotConfig fromEnvironment() {
        String token = require("TELEGRAM_BOT_TOKEN");
        String username = require("TELEGRAM_BOT_USERNAME");
        String dataFile = getenv("DATA_FILE", "data/state.json");
        String adminIdsValue = getenv("ADMIN_IDS", "");
        long minimumActionIntervalMs = Long.parseLong(getenv("MIN_ACTION_INTERVAL_MS", "700"));

        Set<Long> adminIds = Arrays.stream(adminIdsValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toSet());

        return new BotConfig(token, username, Path.of(dataFile), adminIds, minimumActionIntervalMs);
    }

    private static String require(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable " + key + " is required.");
        }
        return value;
    }

    private static String getenv(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
