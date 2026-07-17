package com.videocharter.service;

import com.videocharter.model.AppState;
import com.videocharter.model.Country;
import com.videocharter.model.DomainEnums.Gender;
import com.videocharter.model.DomainEnums.PartnerPreference;
import com.videocharter.model.DomainEnums.ReportStatus;
import com.videocharter.model.MatchRecord;
import com.videocharter.model.ProfileDraft;
import com.videocharter.model.ReportRecord;
import com.videocharter.model.UserAccount;
import com.videocharter.model.UserProfile;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ProfileService {

    public record SubscriptionPricing(int monthlyStars, int yearlyStars) {
    }

    public record LikeResult(boolean created, boolean mutual, UserProfile counterpart) {
    }

    public record ModerationReportView(ReportRecord report, UserProfile reporter, UserProfile target) {
    }

    public record SubscriptionView(UserAccount account, UserProfile profile) {
    }

    public record BanStatus(boolean active, LocalDate until) {
    }

    private final StateStore stateStore;
    private final CountryCatalog countryCatalog;
    private final DailyQuotaService dailyQuotaService;
    private final Set<Long> adminIds;

    public ProfileService(
            StateStore stateStore,
            CountryCatalog countryCatalog,
            DailyQuotaService dailyQuotaService,
            Set<Long> adminIds
    ) {
        this.stateStore = stateStore;
        this.countryCatalog = countryCatalog;
        this.dailyQuotaService = dailyQuotaService;
        this.adminIds = adminIds;
    }

    public UserAccount touchUser(long userId, String username, String firstName) {
        return stateStore.mutate(state -> {
            UserAccount account = state.getUsers().computeIfAbsent(userId, ignored -> {
                UserAccount fresh = new UserAccount();
                fresh.setUserId(userId);
                return fresh;
            });
            account.setUsername(username);
            account.setFirstName(firstName);
            return account;
        });
    }

    public UserAccount getAccount(long userId) {
        return stateStore.read(state -> state.getUsers().get(userId));
    }

    public boolean hasProfile(long userId) {
        return stateStore.read(state -> state.getProfiles().containsKey(userId));
    }

    public UserProfile getProfile(long userId) {
        return stateStore.read(state -> state.getProfiles().get(userId));
    }

    public Optional<UserProfile> findProfile(long userId) {
        return Optional.ofNullable(getProfile(userId));
    }

    public void saveProfile(long userId, String username, ProfileDraft draft) {
        stateStore.mutate(state -> {
            UserProfile existing = state.getProfiles().get(userId);
            String previousCountry = existing == null ? null : existing.getCountryCode();
            UserProfile saved = draft.toProfile(userId, username, existing);
            state.getProfiles().put(userId, saved);
            if (saved.getCountryCode() != null && !saved.getCountryCode().isBlank() && !saved.getCountryCode().equals(previousCountry)) {
                state.getCountryPopularity().merge(saved.getCountryCode(), 1, Integer::sum);
            }
            return null;
        });
    }

    public void deleteProfile(long userId) {
        stateStore.mutate(state -> {
            state.getProfiles().remove(userId);
            state.getLikesBySource().remove(userId);
            state.getLikesBySource().values().forEach(targets -> targets.remove(userId));
            state.getDismissedLikesByTarget().remove(userId);
            state.getDismissedLikesByTarget().values().forEach(sources -> sources.remove(userId));
            state.getMatches().removeIf(match -> match.getFirstUserId() == userId || match.getSecondUserId() == userId);
            return null;
        });
    }

    public List<Country> getSortedCountries() {
        return stateStore.read(state -> countryCatalog.sortedByPopularity(state.getCountryPopularity()));
    }

    public Optional<Country> findCountry(String code) {
        return countryCatalog.findByCode(code);
    }

    public List<UserProfile> findBrowseCandidates(long viewerId, Set<Long> excludedIds, int limit) {
        return stateStore.read(state -> {
            UserProfile viewer = state.getProfiles().get(viewerId);
            if (viewer == null) {
                return List.of();
            }

            Set<Long> excluded = new HashSet<>(excludedIds);
            excluded.add(viewerId);

            return state.getProfiles().values().stream()
                    .filter(candidate -> !excluded.contains(candidate.getUserId()))
                    .filter(candidate -> !isBanned(state, candidate.getUserId()))
                    .filter(candidate -> !hasLiked(state, viewerId, candidate.getUserId()))
                    .filter(candidate -> !isMatched(state, viewerId, candidate.getUserId()))
                    .filter(candidate -> isCompatible(viewer, candidate))
                    .sorted(Comparator.comparing(UserProfile::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(limit)
                    .toList();
        });
    }

    public LikeResult likeProfile(long fromUserId, long toUserId) {
        return stateStore.mutate(state -> {
            if (!state.getProfiles().containsKey(fromUserId) || !state.getProfiles().containsKey(toUserId)) {
                return new LikeResult(false, false, null);
            }

            Set<Long> targets = state.getLikesBySource().computeIfAbsent(fromUserId, ignored -> new HashSet<>());
            boolean created = targets.add(toUserId);
            state.getDismissedLikesByTarget().computeIfAbsent(toUserId, ignored -> new HashSet<>()).remove(fromUserId);

            boolean mutual = hasLiked(state, toUserId, fromUserId);
            if (mutual && !isMatched(state, fromUserId, toUserId)) {
                long first = Math.min(fromUserId, toUserId);
                long second = Math.max(fromUserId, toUserId);
                state.getMatches().add(new MatchRecord(first, second, Instant.now()));
            }
            return new LikeResult(created, mutual, state.getProfiles().get(toUserId));
        });
    }

    public List<UserProfile> getIncomingLikes(long userId) {
        return stateStore.read(state -> {
            Set<Long> dismissed = state.getDismissedLikesByTarget().getOrDefault(userId, Set.of());
            List<UserProfile> result = new ArrayList<>();
            state.getLikesBySource().forEach((sourceId, targets) -> {
                if (targets.contains(userId)
                        && !dismissed.contains(sourceId)
                        && !isMatched(state, sourceId, userId)
                        && state.getProfiles().containsKey(sourceId)
                        && !isBanned(state, sourceId)) {
                    result.add(state.getProfiles().get(sourceId));
                }
            });
            result.sort(Comparator.comparing(UserProfile::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
            return result;
        });
    }

    public int countIncomingLikes(long userId) {
        return getIncomingLikes(userId).size();
    }

    public void dismissIncomingLike(long userId, long sourceUserId) {
        stateStore.mutate(state -> {
            state.getDismissedLikesByTarget()
                    .computeIfAbsent(userId, ignored -> new HashSet<>())
                    .add(sourceUserId);
            return null;
        });
    }

    public boolean hasActiveSubscription(long userId, LocalDate today) {
        return stateStore.read(state -> {
            UserAccount account = state.getUsers().get(userId);
            return account != null
                    && account.getSubscriptionUntil() != null
                    && !account.getSubscriptionUntil().isBefore(today);
        });
    }

    public SubscriptionPricing getSubscriptionPricing() {
        return stateStore.read(state -> new SubscriptionPricing(
                state.getMonthlySubscriptionStars(),
                state.getYearlySubscriptionStars()
        ));
    }

    public void updateSubscriptionPrice(int days, int stars) {
        stateStore.mutate(state -> {
            if (days == 30) {
                state.setMonthlySubscriptionStars(stars);
            } else if (days == 365) {
                state.setYearlySubscriptionStars(stars);
            }
            return null;
        });
    }

    public int getTotalUsersCount() {
        return stateStore.read(state -> state.getUsers().size());
    }

    public int getTotalProfilesCount() {
        return stateStore.read(state -> state.getProfiles().size());
    }

    public int getActiveSubscriptionsCount(LocalDate today) {
        return stateStore.read(state -> (int) state.getUsers().values().stream()
                .filter(account -> account.getSubscriptionUntil() != null && !account.getSubscriptionUntil().isBefore(today))
                .count());
    }

    public int getFreeViewLimit(LocalDate date) {
        return dailyQuotaService.freeLimit(date);
    }

    public List<SubscriptionView> getActiveSubscriptions(LocalDate today) {
        return stateStore.read(state -> state.getUsers().values().stream()
                .filter(account -> account.getSubscriptionUntil() != null && !account.getSubscriptionUntil().isBefore(today))
                .sorted(Comparator
                        .comparing(UserAccount::getSubscriptionUntil, Comparator.reverseOrder())
                        .thenComparingLong(UserAccount::getUserId))
                .map(account -> new SubscriptionView(account, state.getProfiles().get(account.getUserId())))
                .toList());
    }

    public DailyQuotaService.ViewingDecision registerProfileView(long userId, LocalDate date) {
        return stateStore.mutate(state -> {
            UserAccount account = state.getUsers().computeIfAbsent(userId, ignored -> {
                UserAccount fresh = new UserAccount();
                fresh.setUserId(userId);
                return fresh;
            });
            return dailyQuotaService.registerView(account, date);
        });
    }

    public void applySubscription(long userId, int days, LocalDate today) {
        stateStore.mutate(state -> {
            UserAccount account = state.getUsers().computeIfAbsent(userId, ignored -> {
                UserAccount fresh = new UserAccount();
                fresh.setUserId(userId);
                return fresh;
            });
            LocalDate current = account.getSubscriptionUntil();
            LocalDate base = current != null && current.isAfter(today) ? current : today;
            account.setSubscriptionUntil(base.plusDays(days));
            return null;
        });
    }

    public long createReport(
            long reporterUserId,
            long targetUserId,
            com.videocharter.model.DomainEnums.ReportReason reason,
            String evidenceText,
            String evidenceFileId,
            String evidenceFileType
    ) {
        return stateStore.mutate(state -> {
            ReportRecord report = new ReportRecord();
            report.setId(state.getNextReportId());
            state.setNextReportId(state.getNextReportId() + 1);
            report.setReporterUserId(reporterUserId);
            report.setTargetUserId(targetUserId);
            report.setReason(reason);
            report.setEvidenceText(evidenceText);
            report.setEvidenceFileId(evidenceFileId);
            report.setEvidenceFileType(evidenceFileType);
            report.setCreatedAt(Instant.now());
            state.getReports().add(report);
            return report.getId();
        });
    }

    public List<ModerationReportView> getOpenReports() {
        return stateStore.read(state -> state.getReports().stream()
                .filter(report -> report.getStatus() == ReportStatus.OPEN)
                .sorted(Comparator.comparing(ReportRecord::getCreatedAt))
                .map(report -> new ModerationReportView(
                        report,
                        state.getProfiles().get(report.getReporterUserId()),
                        state.getProfiles().get(report.getTargetUserId())
                ))
                .toList());
    }

    public void resolveReport(long reportId, long moderatorId, boolean approved, boolean banTarget) {
        stateStore.mutate(state -> {
            ReportRecord report = state.getReports().stream()
                    .filter(candidate -> candidate.getId() == reportId)
                    .findFirst()
                    .orElse(null);
            if (report == null) {
                return null;
            }

            report.setStatus(approved ? ReportStatus.APPROVED : ReportStatus.REJECTED);
            report.setModeratorId(moderatorId);
            report.setDecisionNote(banTarget ? "Banned target profile" : (approved ? "Approved" : "Rejected"));

            if (banTarget) {
                UserAccount account = state.getUsers().computeIfAbsent(report.getTargetUserId(), ignored -> {
                    UserAccount fresh = new UserAccount();
                    fresh.setUserId(report.getTargetUserId());
                    return fresh;
                });
                account.setBanned(true);
                account.setBannedUntil(LocalDate.now().plusDays(30));
                deleteProfileInsideState(state, report.getTargetUserId());
            }
            return null;
        });
    }

    public Long resetProfileAfterModeration(long reportId, long moderatorId) {
        return stateStore.mutate(state -> {
            ReportRecord report = state.getReports().stream()
                    .filter(candidate -> candidate.getId() == reportId)
                    .findFirst()
                    .orElse(null);
            if (report == null) {
                return null;
            }

            report.setStatus(ReportStatus.APPROVED);
            report.setModeratorId(moderatorId);
            report.setDecisionNote("Profile reset requested");
            long targetUserId = report.getTargetUserId();
            deleteProfileInsideState(state, targetUserId);
            return targetUserId;
        });
    }

    public List<UserAccount> getModerators() {
        return stateStore.read(state -> state.getUsers().values().stream()
                .filter(UserAccount::isModerator)
                .sorted(Comparator.comparingLong(UserAccount::getUserId))
                .toList());
    }

    public List<UserAccount> getAdmins() {
        return stateStore.read(state -> state.getUsers().values().stream()
                .filter(UserAccount::isAdmin)
                .sorted(Comparator.comparingLong(UserAccount::getUserId))
                .toList());
    }

    public List<UserAccount> getAllUsers() {
        return stateStore.read(state -> state.getUsers().values().stream()
                .sorted(Comparator
                        .comparing((UserAccount account) -> account.getFirstName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparingLong(UserAccount::getUserId))
                .toList());
    }

    public List<UserAccount> getBannedUsers(LocalDate today) {
        return stateStore.mutate(state -> {
            state.getUsers().values().forEach(account -> normalizeBanState(account, today));
            return state.getUsers().values().stream()
                    .filter(UserAccount::isBanned)
                    .sorted(Comparator
                            .comparing(UserAccount::getBannedUntil, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparingLong(UserAccount::getUserId))
                    .toList();
        });
    }

    public void setModerator(long userId, boolean moderator) {
        stateStore.mutate(state -> {
            UserAccount account = state.getUsers().computeIfAbsent(userId, ignored -> {
                UserAccount fresh = new UserAccount();
                fresh.setUserId(userId);
                return fresh;
            });
            account.setModerator(moderator);
            return null;
        });
    }

    public void setAdmin(long userId, boolean admin) {
        stateStore.mutate(state -> {
            UserAccount account = state.getUsers().computeIfAbsent(userId, ignored -> {
                UserAccount fresh = new UserAccount();
                fresh.setUserId(userId);
                return fresh;
            });
            account.setAdmin(admin);
            return null;
        });
    }

    public boolean isAdmin(long userId) {
        return adminIds.contains(userId) || stateStore.read(state -> {
            UserAccount account = state.getUsers().get(userId);
            return account != null && account.isAdmin();
        });
    }

    public boolean isModerator(long userId) {
        return isAdmin(userId) || stateStore.read(state -> {
            UserAccount account = state.getUsers().get(userId);
            return account != null && account.isModerator();
        });
    }

    public boolean isBanned(long userId) {
        return getBanStatus(userId, LocalDate.now()).active();
    }

    public BanStatus getBanStatus(long userId, LocalDate today) {
        return stateStore.mutate(state -> {
            UserAccount account = state.getUsers().get(userId);
            if (account == null) {
                return new BanStatus(false, null);
            }
            normalizeBanState(account, today);
            return new BanStatus(account.isBanned(), account.getBannedUntil());
        });
    }

    public void unbanUser(long userId) {
        stateStore.mutate(state -> {
            UserAccount account = state.getUsers().get(userId);
            if (account != null) {
                account.setBanned(false);
                account.setBannedUntil(null);
            }
            return null;
        });
    }

    public boolean isMatched(long firstUserId, long secondUserId) {
        return stateStore.read(state -> isMatched(state, firstUserId, secondUserId));
    }

    private boolean isCompatible(UserProfile viewer, UserProfile candidate) {
        return matchesPreference(candidate.getGender(), viewer.getLookingFor())
                && matchesPreference(viewer.getGender(), candidate.getLookingFor())
                && candidate.getAge() >= viewer.getPreferredAgeMin()
                && candidate.getAge() <= viewer.getPreferredAgeMax()
                && viewer.getAge() >= candidate.getPreferredAgeMin()
                && viewer.getAge() <= candidate.getPreferredAgeMax();
    }

    private boolean matchesPreference(Gender gender, PartnerPreference preference) {
        return switch (preference) {
            case EVERYONE -> true;
            case MEN -> gender == Gender.MALE;
            case WOMEN -> gender == Gender.FEMALE;
        };
    }

    private boolean hasLiked(AppState state, long sourceUserId, long targetUserId) {
        return state.getLikesBySource().getOrDefault(sourceUserId, Set.of()).contains(targetUserId);
    }

    private boolean isMatched(AppState state, long firstUserId, long secondUserId) {
        long first = Math.min(firstUserId, secondUserId);
        long second = Math.max(firstUserId, secondUserId);
        return state.getMatches().stream()
                .anyMatch(match -> match.getFirstUserId() == first && match.getSecondUserId() == second);
    }

    private boolean isBanned(AppState state, long userId) {
        UserAccount account = state.getUsers().get(userId);
        if (account == null) {
            return false;
        }
        normalizeBanState(account, LocalDate.now());
        return account.isBanned();
    }

    private void normalizeBanState(UserAccount account, LocalDate today) {
        if (account == null) {
            return;
        }
        if (!account.isBanned()) {
            account.setBannedUntil(null);
            return;
        }
        if (account.getBannedUntil() == null) {
            account.setBannedUntil(today.plusDays(30));
            return;
        }
        if (!today.isBefore(account.getBannedUntil())) {
            account.setBanned(false);
            account.setBannedUntil(null);
        }
    }

    private void deleteProfileInsideState(AppState state, long userId) {
        state.getProfiles().remove(userId);
        state.getLikesBySource().remove(userId);
        state.getLikesBySource().values().forEach(targets -> targets.remove(userId));
        state.getDismissedLikesByTarget().remove(userId);
        state.getDismissedLikesByTarget().values().forEach(sources -> sources.remove(userId));
        state.getMatches().removeIf(match -> match.getFirstUserId() == userId || match.getSecondUserId() == userId);
    }
}
