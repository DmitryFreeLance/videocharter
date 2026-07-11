package com.videocharter.service;

import com.videocharter.model.UserAccount;
import java.time.DayOfWeek;
import java.time.LocalDate;

public class DailyQuotaService {

    public record ViewingDecision(int freeLimit, int viewedToday, boolean adRequired) {
    }

    public int freeLimit(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return switch (day) {
            case MONDAY, TUESDAY -> 10;
            case WEDNESDAY, THURSDAY, FRIDAY -> 15;
            case SATURDAY, SUNDAY -> 20;
        };
    }

    public ViewingDecision registerView(UserAccount account, LocalDate date) {
        if (account.getLastViewDate() == null || !account.getLastViewDate().equals(date)) {
            account.setLastViewDate(date);
            account.setViewsToday(0);
        }

        account.setViewsToday(account.getViewsToday() + 1);
        int freeLimit = freeLimit(date);
        int postLimitIndex = Math.max(0, account.getViewsToday() - freeLimit);
        boolean adRequired = postLimitIndex > 0;
        return new ViewingDecision(freeLimit, account.getViewsToday(), adRequired);
    }
}
