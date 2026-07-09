package com.videocharter.config;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record BotConfig(
        String token,
        String username,
        Path dataFile,
        Set<Long> adminIds,
        long minimumActionIntervalMs,
        boolean adsgramEnabled,
        String adsgramToken,
        List<String> adsgramBlockIds,
        String adsgramLanguage,
        int adsgramCandidatesPerBlock
) {

    public static BotConfig fromEnvironment() {
        String token = require("TELEGRAM_BOT_TOKEN");
        String username = require("TELEGRAM_BOT_USERNAME");
        String dataFile = getenv("DATA_FILE", "data/state.json");
        String adminIdsValue = getenv("ADMIN_IDS", "");
        long minimumActionIntervalMs = Long.parseLong(getenv("MIN_ACTION_INTERVAL_MS", "700"));
        String adsgramToken = normalizeNullable(System.getenv("ADSGRAM_TOKEN"));
        List<String> adsgramBlockIds = parseAdsgramBlockIds(
                System.getenv("ADSGRAM_BLOCK_IDS"),
                System.getenv("ADSGRAM_BLOCK_ID")
        );
        String adsgramLanguage = parseLanguage(System.getenv("ADSGRAM_LANGUAGE"));
        int adsgramCandidatesPerBlock = parsePositiveInt(System.getenv("ADSGRAM_CANDIDATES_PER_BLOCK"), 2);

        Set<Long> adminIds = Arrays.stream(adminIdsValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toSet());

        boolean adsgramReady = adsgramToken != null && !adsgramBlockIds.isEmpty();
        boolean adsgramEnabled = parseBoolean(System.getenv("ADSGRAM_ENABLED"), adsgramReady);
        if (!adsgramReady) {
            adsgramEnabled = false;
        }

        return new BotConfig(
                token,
                username,
                Path.of(dataFile),
                adminIds,
                minimumActionIntervalMs,
                adsgramEnabled,
                adsgramToken,
                List.copyOf(adsgramBlockIds),
                adsgramLanguage,
                adsgramCandidatesPerBlock
        );
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

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> parseAdsgramBlockIds(String rawMany, String rawSingle) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        collectAdsgramBlockIds(unique, rawMany);
        collectAdsgramBlockIds(unique, rawSingle);
        return unique.isEmpty() ? List.of() : List.copyOf(unique);
    }

    private static void collectAdsgramBlockIds(Set<String> target, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            String normalized = normalizeBlockId(part);
            if (normalized != null) {
                target.add(normalized);
            }
        }
    }

    private static String normalizeBlockId(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("bot-")) {
            value = value.substring(4);
        }
        String digitsOnly = value.replaceAll("[^0-9]", "");
        return digitsOnly.isEmpty() ? null : digitsOnly;
    }

    private static String parseLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int dash = value.indexOf('-');
        if (dash > 0) {
            value = value.substring(0, dash);
        }
        int underscore = value.indexOf('_');
        if (underscore > 0) {
            value = value.substring(0, underscore);
        }
        return value.length() < 2 ? null : value.substring(0, 2);
    }

    private static int parsePositiveInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "1", "true", "yes", "y", "on" -> true;
            case "0", "false", "no", "n", "off" -> false;
            default -> fallback;
        };
    }
}
