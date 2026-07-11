package com.videocharter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videocharter.config.BotConfig;
import com.videocharter.model.AdsgramAd;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdsgramService {
    private static final Logger log = LoggerFactory.getLogger(AdsgramService.class);
    private static final Pattern ANCHOR_TAG_PATTERN = Pattern.compile("</?a(?:\\s+[^>]*)?>", Pattern.CASE_INSENSITIVE);

    private static final List<String> PRIORITY_1_VIDEO_SOCIAL = List.of(
            "video", "shorts", "reels", "stream", "messenger", "telegram", "chat", "social"
    );
    private static final List<String> PRIORITY_2_DATING = List.of(
            "dating", "meet", "relationship", "friends", "знакомств", "dating app"
    );
    private static final List<String> PRIORITY_3_APPS = List.of(
            "mini app", "mini game", "app", "bot", "web3 game", "play to earn"
    );
    private static final List<String> PRIORITY_4_FINANCE = List.of(
            "crypto", "wallet", "investment", "trading", "exchange", "крипто", "инвести"
    );
    private static final List<String> PRIORITY_5_GAMBLING = List.of(
            "casino", "bet", "sportsbook", "slot", "roulette", "poker", "ставк", "казино"
    );

    private final BotConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile AdsgramAd cachedAd;

    public AdsgramService(BotConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isEnabled() {
        return config.adsgramEnabled();
    }

    public Optional<AdsgramAd> pickBestAd(long telegramUserId, String userLanguageCode) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        String language = normalizeLanguageCode(userLanguageCode);
        if (language == null) {
            language = normalizeLanguageCode(config.adsgramLanguage());
        }
        if (language == null) {
            language = "en";
        }

        List<AdsgramAd> candidates = new ArrayList<>();
        for (String blockId : config.adsgramBlockIds()) {
            for (int attempt = 0; attempt < config.adsgramCandidatesPerBlock(); attempt++) {
                requestAd(telegramUserId, blockId, language).ifPresent(candidates::add);
            }
        }

        Optional<AdsgramAd> best = candidates.stream()
                .max(Comparator.comparingInt(AdsgramAd::getPriorityScore));
        if (best.isPresent()) {
            cachedAd = best.get();
            return best;
        }
        return Optional.ofNullable(cachedAd);
    }

    private Optional<AdsgramAd> requestAd(long telegramUserId, String blockId, String language) {
        try {
            String url = "https://api.adsgram.ai/advbot?tgid=" + telegramUserId
                    + "&blockid=" + URLEncoder.encode(blockId, StandardCharsets.UTF_8)
                    + "&language=" + URLEncoder.encode(language, StandardCharsets.UTF_8)
                    + "&token=" + URLEncoder.encode(config.adsgramToken(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String textHtml = stripAnchorTags(clean(root.path("text_html").asText(null)));
            if (textHtml == null || textHtml.isBlank()) {
                return Optional.empty();
            }

            String clickUrl = clean(root.path("click_url").asText(null));
            String buttonName = clean(root.path("button_name").asText(null));
            String rewardUrl = clean(root.path("reward_url").asText(null));
            String rewardButtonName = clean(root.path("button_reward_name").asText(null));
            String imageUrl = clean(root.path("image_url").asText(null));
            int score = scoreAd(textHtml, clickUrl, buttonName, rewardUrl, rewardButtonName, imageUrl);

            return Optional.of(new AdsgramAd(
                    textHtml,
                    clickUrl,
                    buttonName,
                    rewardUrl,
                    rewardButtonName,
                    imageUrl,
                    blockId,
                    score
            ));
        } catch (Exception exception) {
            log.debug("Adsgram request failed for block {}", blockId, exception);
            return Optional.empty();
        }
    }

    private String normalizeLanguageCode(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }

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

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stripAnchorTags(String value) {
        if (value == null) {
            return null;
        }
        return ANCHOR_TAG_PATTERN.matcher(value).replaceAll("");
    }

    private int scoreAd(String textHtml,
                        String clickUrl,
                        String buttonName,
                        String rewardUrl,
                        String rewardButtonName,
                        String imageUrl) {
        String payload = String.join(" ",
                safeLower(textHtml),
                safeLower(clickUrl),
                safeLower(buttonName),
                safeLower(rewardUrl),
                safeLower(rewardButtonName),
                safeLower(imageUrl)
        );

        if (containsAny(payload, PRIORITY_1_VIDEO_SOCIAL)) {
            return 500;
        }
        if (containsAny(payload, PRIORITY_2_DATING)) {
            return 450;
        }
        if (containsAny(payload, PRIORITY_3_APPS)) {
            return 350;
        }
        if (containsAny(payload, PRIORITY_4_FINANCE)) {
            return 200;
        }
        if (containsAny(payload, PRIORITY_5_GAMBLING)) {
            return 100;
        }
        return 300;
    }

    private boolean containsAny(String haystack, List<String> needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
