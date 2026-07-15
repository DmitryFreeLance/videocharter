package com.videocharter.service;

import com.videocharter.model.DomainEnums.ReportReason;
import com.videocharter.model.ProfileDraft;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SessionService {

    public enum ExpectedInput {
        NONE,
        NAME,
        ABOUT,
        AGE,
        AGE_RANGE,
        MEDIA_PHOTO,
        MEDIA_VIDEO,
        REPORT_EVIDENCE,
        ADD_ADMIN_ID,
        REMOVE_ADMIN_ID,
        ADD_MODERATOR_ID,
        REMOVE_MODERATOR_ID,
        SUBSCRIPTION_PRICE
    }

    public static class UserSession {
        public enum ScreenKind {
            TEXT,
            PHOTO,
            VIDEO
        }

        public enum ProfileScreenContext {
            NONE,
            MY_PROFILE,
            BROWSE,
            LIKES,
            MODERATION_REPORT,
            DRAFT_PREVIEW
        }

        private Integer menuMessageId;
        private ScreenKind screenKind = ScreenKind.TEXT;
        private ProfileScreenContext profileScreenContext = ProfileScreenContext.NONE;
        private Long currentScreenProfileUserId;
        private int currentMediaIndex;
        private final List<Integer> activeCardMessageIds = new ArrayList<>();
        private final Deque<Long> browseHistory = new ArrayDeque<>();
        private final Set<Long> seenProfileIds = new HashSet<>();
        private ProfileDraft draft;
        private ExpectedInput expectedInput = ExpectedInput.NONE;
        private int countryPage;
        private Long currentBrowseProfileId;
        private Long pendingProfileAfterAd;
        private boolean adInterstitialActive;
        private Long reportTargetUserId;
        private ReportReason reportReason;
        private String reportEvidenceText;
        private String reportEvidenceFileId;
        private String reportEvidenceFileType;
        private int moderationIndex;
        private int subscriptionsPage;
        private int adminUsersPage;
        private int bannedUsersPage;
        private Integer pendingSubscriptionPriceDays;
        private Integer subscriptionInvoiceMessageId;
        private Integer wizardPromptMessageId;
        private final Map<String, String> currentButtonActions = new LinkedHashMap<>();

        public Integer getMenuMessageId() {
            return menuMessageId;
        }

        public void setMenuMessageId(Integer menuMessageId) {
            this.menuMessageId = menuMessageId;
        }

        public ScreenKind getScreenKind() {
            return screenKind;
        }

        public void setScreenKind(ScreenKind screenKind) {
            this.screenKind = screenKind;
        }

        public ProfileScreenContext getProfileScreenContext() {
            return profileScreenContext;
        }

        public void setProfileScreenContext(ProfileScreenContext profileScreenContext) {
            this.profileScreenContext = profileScreenContext;
        }

        public Long getCurrentScreenProfileUserId() {
            return currentScreenProfileUserId;
        }

        public void setCurrentScreenProfileUserId(Long currentScreenProfileUserId) {
            this.currentScreenProfileUserId = currentScreenProfileUserId;
        }

        public int getCurrentMediaIndex() {
            return currentMediaIndex;
        }

        public void setCurrentMediaIndex(int currentMediaIndex) {
            this.currentMediaIndex = currentMediaIndex;
        }

        public List<Integer> getActiveCardMessageIds() {
            return activeCardMessageIds;
        }

        public Deque<Long> getBrowseHistory() {
            return browseHistory;
        }

        public Set<Long> getSeenProfileIds() {
            return seenProfileIds;
        }

        public ProfileDraft getDraft() {
            return draft;
        }

        public void setDraft(ProfileDraft draft) {
            this.draft = draft;
        }

        public ExpectedInput getExpectedInput() {
            return expectedInput;
        }

        public void setExpectedInput(ExpectedInput expectedInput) {
            this.expectedInput = expectedInput;
        }

        public int getCountryPage() {
            return countryPage;
        }

        public void setCountryPage(int countryPage) {
            this.countryPage = countryPage;
        }

        public Long getCurrentBrowseProfileId() {
            return currentBrowseProfileId;
        }

        public void setCurrentBrowseProfileId(Long currentBrowseProfileId) {
            this.currentBrowseProfileId = currentBrowseProfileId;
        }

        public Long getPendingProfileAfterAd() {
            return pendingProfileAfterAd;
        }

        public void setPendingProfileAfterAd(Long pendingProfileAfterAd) {
            this.pendingProfileAfterAd = pendingProfileAfterAd;
        }

        public boolean isAdInterstitialActive() {
            return adInterstitialActive;
        }

        public void setAdInterstitialActive(boolean adInterstitialActive) {
            this.adInterstitialActive = adInterstitialActive;
        }

        public Long getReportTargetUserId() {
            return reportTargetUserId;
        }

        public void setReportTargetUserId(Long reportTargetUserId) {
            this.reportTargetUserId = reportTargetUserId;
        }

        public ReportReason getReportReason() {
            return reportReason;
        }

        public void setReportReason(ReportReason reportReason) {
            this.reportReason = reportReason;
        }

        public String getReportEvidenceText() {
            return reportEvidenceText;
        }

        public void setReportEvidenceText(String reportEvidenceText) {
            this.reportEvidenceText = reportEvidenceText;
        }

        public String getReportEvidenceFileId() {
            return reportEvidenceFileId;
        }

        public void setReportEvidenceFileId(String reportEvidenceFileId) {
            this.reportEvidenceFileId = reportEvidenceFileId;
        }

        public String getReportEvidenceFileType() {
            return reportEvidenceFileType;
        }

        public void setReportEvidenceFileType(String reportEvidenceFileType) {
            this.reportEvidenceFileType = reportEvidenceFileType;
        }

        public int getModerationIndex() {
            return moderationIndex;
        }

        public void setModerationIndex(int moderationIndex) {
            this.moderationIndex = moderationIndex;
        }

        public int getSubscriptionsPage() {
            return subscriptionsPage;
        }

        public void setSubscriptionsPage(int subscriptionsPage) {
            this.subscriptionsPage = subscriptionsPage;
        }

        public int getAdminUsersPage() {
            return adminUsersPage;
        }

        public void setAdminUsersPage(int adminUsersPage) {
            this.adminUsersPage = adminUsersPage;
        }

        public int getBannedUsersPage() {
            return bannedUsersPage;
        }

        public void setBannedUsersPage(int bannedUsersPage) {
            this.bannedUsersPage = bannedUsersPage;
        }

        public Integer getPendingSubscriptionPriceDays() {
            return pendingSubscriptionPriceDays;
        }

        public void setPendingSubscriptionPriceDays(Integer pendingSubscriptionPriceDays) {
            this.pendingSubscriptionPriceDays = pendingSubscriptionPriceDays;
        }

        public Integer getSubscriptionInvoiceMessageId() {
            return subscriptionInvoiceMessageId;
        }

        public void setSubscriptionInvoiceMessageId(Integer subscriptionInvoiceMessageId) {
            this.subscriptionInvoiceMessageId = subscriptionInvoiceMessageId;
        }

        public Integer getWizardPromptMessageId() {
            return wizardPromptMessageId;
        }

        public void setWizardPromptMessageId(Integer wizardPromptMessageId) {
            this.wizardPromptMessageId = wizardPromptMessageId;
        }

        public Map<String, String> getCurrentButtonActions() {
            return currentButtonActions;
        }

        public void resetReportDraft() {
            reportTargetUserId = null;
            reportReason = null;
            reportEvidenceText = null;
            reportEvidenceFileId = null;
            reportEvidenceFileType = null;
        }

        public void resetBrowsing() {
            browseHistory.clear();
            seenProfileIds.clear();
            currentBrowseProfileId = null;
            pendingProfileAfterAd = null;
            adInterstitialActive = false;
        }

        public void resetProfileScreen() {
            profileScreenContext = ProfileScreenContext.NONE;
            currentScreenProfileUserId = null;
            currentMediaIndex = 0;
            screenKind = ScreenKind.TEXT;
        }
    }

    private final ConcurrentMap<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public UserSession get(long userId) {
        return sessions.computeIfAbsent(userId, ignored -> new UserSession());
    }
}
