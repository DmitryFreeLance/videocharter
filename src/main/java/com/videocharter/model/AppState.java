package com.videocharter.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppState {

    private Map<Long, UserProfile> profiles = new HashMap<>();
    private Map<Long, UserAccount> users = new HashMap<>();
    private Map<Long, Set<Long>> likesBySource = new HashMap<>();
    private Map<Long, Set<Long>> dismissedLikesByTarget = new HashMap<>();
    private Map<String, Integer> countryPopularity = new HashMap<>();
    private List<MatchRecord> matches = new ArrayList<>();
    private List<ReportRecord> reports = new ArrayList<>();
    private long nextReportId = 1L;
    private int monthlySubscriptionStars = 30;
    private int yearlySubscriptionStars = 300;

    public Map<Long, UserProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<Long, UserProfile> profiles) {
        this.profiles = profiles;
    }

    public Map<Long, UserAccount> getUsers() {
        return users;
    }

    public void setUsers(Map<Long, UserAccount> users) {
        this.users = users;
    }

    public Map<Long, Set<Long>> getLikesBySource() {
        return likesBySource;
    }

    public void setLikesBySource(Map<Long, Set<Long>> likesBySource) {
        this.likesBySource = likesBySource;
    }

    public Map<Long, Set<Long>> getDismissedLikesByTarget() {
        return dismissedLikesByTarget;
    }

    public void setDismissedLikesByTarget(Map<Long, Set<Long>> dismissedLikesByTarget) {
        this.dismissedLikesByTarget = dismissedLikesByTarget;
    }

    public Map<String, Integer> getCountryPopularity() {
        return countryPopularity;
    }

    public void setCountryPopularity(Map<String, Integer> countryPopularity) {
        this.countryPopularity = countryPopularity;
    }

    public List<MatchRecord> getMatches() {
        return matches;
    }

    public void setMatches(List<MatchRecord> matches) {
        this.matches = matches;
    }

    public List<ReportRecord> getReports() {
        return reports;
    }

    public void setReports(List<ReportRecord> reports) {
        this.reports = reports;
    }

    public long getNextReportId() {
        return nextReportId;
    }

    public void setNextReportId(long nextReportId) {
        this.nextReportId = nextReportId;
    }

    public int getMonthlySubscriptionStars() {
        return monthlySubscriptionStars;
    }

    public void setMonthlySubscriptionStars(int monthlySubscriptionStars) {
        this.monthlySubscriptionStars = monthlySubscriptionStars;
    }

    public int getYearlySubscriptionStars() {
        return yearlySubscriptionStars;
    }

    public void setYearlySubscriptionStars(int yearlySubscriptionStars) {
        this.yearlySubscriptionStars = yearlySubscriptionStars;
    }

    public void normalize() {
        if (profiles == null) {
            profiles = new HashMap<>();
        }
        if (users == null) {
            users = new HashMap<>();
        }
        if (likesBySource == null) {
            likesBySource = new HashMap<>();
        }
        if (dismissedLikesByTarget == null) {
            dismissedLikesByTarget = new HashMap<>();
        }
        if (countryPopularity == null) {
            countryPopularity = new HashMap<>();
        }
        if (matches == null) {
            matches = new ArrayList<>();
        }
        if (reports == null) {
            reports = new ArrayList<>();
        }
        if (monthlySubscriptionStars <= 0) {
            monthlySubscriptionStars = 30;
        }
        if (yearlySubscriptionStars <= 0) {
            yearlySubscriptionStars = 300;
        }
        likesBySource.replaceAll((key, value) -> value == null ? new HashSet<>() : value);
        dismissedLikesByTarget.replaceAll((key, value) -> value == null ? new HashSet<>() : value);
    }
}
