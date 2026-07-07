package com.videocharter.model;

import java.time.LocalDate;

public class UserAccount {

    private long userId;
    private String username;
    private String firstName;
    private boolean moderator;
    private boolean banned;
    private LocalDate subscriptionUntil;
    private LocalDate lastViewDate;
    private int viewsToday;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public boolean isModerator() {
        return moderator;
    }

    public void setModerator(boolean moderator) {
        this.moderator = moderator;
    }

    public boolean isBanned() {
        return banned;
    }

    public void setBanned(boolean banned) {
        this.banned = banned;
    }

    public LocalDate getSubscriptionUntil() {
        return subscriptionUntil;
    }

    public void setSubscriptionUntil(LocalDate subscriptionUntil) {
        this.subscriptionUntil = subscriptionUntil;
    }

    public LocalDate getLastViewDate() {
        return lastViewDate;
    }

    public void setLastViewDate(LocalDate lastViewDate) {
        this.lastViewDate = lastViewDate;
    }

    public int getViewsToday() {
        return viewsToday;
    }

    public void setViewsToday(int viewsToday) {
        this.viewsToday = viewsToday;
    }
}
