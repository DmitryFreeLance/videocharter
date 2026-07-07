package com.videocharter;

import com.videocharter.bot.VideoCharterBot;
import com.videocharter.config.BotConfig;
import com.videocharter.service.CountryCatalog;
import com.videocharter.service.DailyQuotaService;
import com.videocharter.service.ProfileService;
import com.videocharter.service.RateLimiterService;
import com.videocharter.service.SessionService;
import com.videocharter.service.StateStore;
import com.videocharter.ui.UiFactory;
import java.time.Duration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public final class VideoCharterApplication {

    private VideoCharterApplication() {
    }

    public static void main(String[] args) throws Exception {
        BotConfig config = BotConfig.fromEnvironment();
        StateStore stateStore = new StateStore(config.dataFile());
        CountryCatalog countryCatalog = new CountryCatalog();
        DailyQuotaService quotaService = new DailyQuotaService();
        ProfileService profileService = new ProfileService(stateStore, countryCatalog, quotaService, config.adminIds());
        SessionService sessionService = new SessionService();
        RateLimiterService limiterService = new RateLimiterService(Duration.ofMillis(config.minimumActionIntervalMs()));
        UiFactory uiFactory = new UiFactory();

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new VideoCharterBot(
                config,
                profileService,
                sessionService,
                limiterService,
                uiFactory,
                countryCatalog
        ));

        System.out.println("VideoCharter bot is running.");
    }
}
