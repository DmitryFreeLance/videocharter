package com.videocharter.ui;

import com.videocharter.model.ProfileDraft;
import com.videocharter.model.ReportRecord;
import com.videocharter.model.UserAccount;
import com.videocharter.model.UserProfile;
import com.videocharter.service.ProfileService.SubscriptionPricing;
import com.videocharter.service.ProfileService.SubscriptionView;
import com.videocharter.util.Htmls;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

public class UiFactory {

    public record ButtonSpec(String text, String callbackData, String url) {
        public static ButtonSpec callback(String text, String callbackData) {
            return new ButtonSpec(text, callbackData, null);
        }

        public static ButtonSpec url(String text, String url) {
            return new ButtonSpec(text, null, url);
        }
    }

    public InlineKeyboardMarkup keyboard(List<List<ButtonSpec>> rows) {
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();
        for (List<ButtonSpec> row : rows) {
            List<InlineKeyboardButton> buttons = new ArrayList<>();
            for (ButtonSpec spec : row) {
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(spec.text());
                if (spec.callbackData() != null) {
                    button.setCallbackData(spec.callbackData());
                }
                if (spec.url() != null) {
                    button.setUrl(spec.url());
                }
                buttons.add(button);
            }
            keyboardRows.add(buttons);
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboardRows);
        return markup;
    }

    public String homeText(UserAccount account, boolean hasProfile, int likesCount, boolean adsDisabled, boolean moderationEnabled) {
        StringBuilder builder = new StringBuilder();
        builder.append("<b>🌍 Video Chatter</b>\n");
        builder.append("Meet new people around the world in a clean, fast and friendly flow.\n\n");
        builder.append("Hey, ").append(Htmls.escape(displayName(account))).append(".\n");
        builder.append(hasProfile ? "Your profile is ready to go.\n" : "You have not created your profile yet.\n");
        builder.append("💌 Incoming likes: <b>").append(likesCount).append("</b>\n");
        builder.append("📢 Ads: <b>").append(adsDisabled ? "Off" : "On").append("</b>\n");
        if (moderationEnabled) {
            builder.append("🛡 Moderation access: <b>Enabled</b>\n");
        }
        builder.append("\n<b>Rules</b>\n");
        builder.append("🚫 Forbidden: minors, nude photos, harassment, spam, fraud, threats, illegal content and impersonation.\n");
        builder.append("🔄 If your profile contains nude photos, it may be removed and sent back for editing.\n");
        builder.append("🔞 18+ only. By using this bot, you confirm that you are 18 or older and accept the rules.\n");
        builder.append("⚠️ We are not responsible for user behavior, meetings, conversations, payments outside Telegram, or content posted by users.\n");
        builder.append("\nChoose an action below and the bot will keep the chat tidy by updating the current screen.");
        return builder.toString();
    }

    public String profileSummary(UserProfile profile, boolean ownProfile) {
        StringBuilder builder = new StringBuilder();
        builder.append(ownProfile ? "<b>My profile</b>\n" : "<b>Profile</b>\n");
        builder.append(Htmls.escape(profile.getName())).append(", ").append(profile.getAge()).append("\n");
        builder.append(profile.getCountryFlag()).append(" ").append(Htmls.escape(profile.getCountryName())).append("\n");
        if (profile.getAbout() != null && !profile.getAbout().isBlank()) {
            builder.append("About: <b>").append(Htmls.escape(profile.getAbout())).append("</b>\n");
        }
        builder.append("Gender: <b>").append(profile.getGender().label()).append("</b>\n");
        builder.append("Looking for: <b>").append(profile.getLookingFor().label()).append("</b>\n");
        builder.append("Goal: <b>").append(profile.getGoal().label()).append("</b>\n");
        builder.append("Preferred age: <b>").append(profile.getPreferredAgeMin()).append("-").append(profile.getPreferredAgeMax()).append("</b>\n");
        builder.append("Privacy: <b>").append(profile.getPrivacyMode().label()).append("</b>\n");
        builder.append("Media: <b>").append(profile.getMedia().size()).append("</b>");
        if (profile.getPrivacyMode().name().equals("OPEN") && profile.getUsername() != null && !profile.getUsername().isBlank()) {
            builder.append("\nUsername: @").append(Htmls.escape(profile.getUsername()));
        } else if (profile.getPrivacyMode().name().equals("PRIVATE")) {
            builder.append("\nUsername: <b>hidden</b>");
        }
        return builder.toString();
    }

    public String browseCard(UserProfile profile) {
        StringBuilder builder = new StringBuilder();
        builder.append(Htmls.escape(profile.getName())).append(", ").append(profile.getAge()).append(", ");
        builder.append(Htmls.escape(profile.getCountryName() == null ? "" : profile.getCountryName()));
        if (profile.getAbout() != null && !profile.getAbout().isBlank()) {
            builder.append(" - ").append(Htmls.escape(profile.getAbout()));
        }
        return builder.toString();
    }

    public String wizardText(ProfileDraft draft, String extra) {
        StringBuilder builder = new StringBuilder();
        builder.append("<b>🧩 Profile setup</b>\n");
        if (extra != null && !extra.isBlank()) {
            builder.append(extra).append("\n\n");
        }
        builder.append(compactDraft(draft));
        return builder.toString();
    }

    public String subscriptionText(UserAccount account, SubscriptionPricing pricing) {
        StringBuilder builder = new StringBuilder();
        builder.append("<b>💎 Disable ads</b>\n");
        builder.append("After the free daily limit, the bot shows one short ad before each next profile.\n\n");
        if (account.getSubscriptionUntil() != null) {
            builder.append("Current plan active until <b>")
                    .append(account.getSubscriptionUntil().format(DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH)))
                    .append("</b>.\n\n");
        }
        builder.append("Plans:\n");
        builder.append("• ").append(pricing.monthlyStars()).append(" Stars / 30 days\n");
        builder.append("• ").append(pricing.yearlyStars()).append(" Stars / 365 days\n\n");
        builder.append("Payments use Telegram Stars.");
        return builder.toString();
    }

    public String adInterstitialText(int freeLimit, int viewedToday) {
        return "<b>📣 Ad break</b>\nYou have already viewed " + viewedToday + " profiles today. "
                + "Your free limit for today is " + freeLimit + ".\n\n"
                + "After the free daily limit, one short interstitial appears before each next profile.";
    }

    public String moderationReportText(ReportRecord report, UserProfile reporter, UserProfile target) {
        String reporterName = reporter == null ? "Unknown" : reporter.getName();
        String targetName = target == null ? "Unknown" : target.getName();
        StringBuilder builder = new StringBuilder();
        builder.append("<b>🛡 Open report #").append(report.getId()).append("</b>\n");
        builder.append("Reporter: ").append(Htmls.escape(reporterName)).append(" (").append(report.getReporterUserId()).append(")\n");
        builder.append("Target: ").append(Htmls.escape(targetName)).append(" (").append(report.getTargetUserId()).append(")\n");
        builder.append("Reason: <b>").append(report.getReason().label()).append("</b>\n");
        if (report.getEvidenceText() != null && !report.getEvidenceText().isBlank()) {
            builder.append("Evidence: ").append(Htmls.escape(report.getEvidenceText())).append("\n");
        }
        if (report.getEvidenceFileId() != null) {
            builder.append("Media evidence attached.\n");
        }
        builder.append("Created: ").append(report.getCreatedAt());
        return builder.toString();
    }

    public String adminOverviewText(
            int totalUsers,
            int totalProfiles,
            int activeSubscriptions,
            SubscriptionPricing pricing
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("<b>📊 Admin overview</b>\n");
        builder.append("Users in bot: <b>").append(totalUsers).append("</b>\n");
        builder.append("Profiles created: <b>").append(totalProfiles).append("</b>\n");
        builder.append("Active subscriptions: <b>").append(activeSubscriptions).append("</b>\n\n");
        builder.append("Current prices:\n");
        builder.append("• 30 days: <b>").append(pricing.monthlyStars()).append(" Stars</b>\n");
        builder.append("• 365 days: <b>").append(pricing.yearlyStars()).append(" Stars</b>");
        return builder.toString();
    }

    public String adminPricingText(SubscriptionPricing pricing, String extra) {
        StringBuilder builder = new StringBuilder();
        builder.append("<b>💸 Subscription pricing</b>\n");
        if (extra != null && !extra.isBlank()) {
            builder.append(extra).append("\n\n");
        }
        builder.append("30 days: <b>").append(pricing.monthlyStars()).append(" Stars</b>\n");
        builder.append("365 days: <b>").append(pricing.yearlyStars()).append(" Stars</b>\n\n");
        builder.append("Choose a plan to update, then send a new integer price.");
        return builder.toString();
    }

    public String activeSubscriptionsText(List<SubscriptionView> subscriptions, int page, int totalPages) {
        StringBuilder builder = new StringBuilder();
        builder.append("<b>💳 Active subscriptions</b>\n");
        builder.append("Page <b>").append(page + 1).append("/").append(totalPages).append("</b>\n\n");
        for (SubscriptionView view : subscriptions) {
            UserAccount account = view.account();
            UserProfile profile = view.profile();
            builder.append("• ").append(account.getUserId());
            if (profile != null && profile.getName() != null) {
                builder.append(" — ").append(Htmls.escape(profile.getName()));
            } else if (account.getFirstName() != null) {
                builder.append(" — ").append(Htmls.escape(account.getFirstName()));
            }
            builder.append("\n");
            if (account.getUsername() != null && !account.getUsername().isBlank()) {
                builder.append("  @").append(Htmls.escape(account.getUsername())).append("\n");
            }
            builder.append("  Until: <b>")
                    .append(account.getSubscriptionUntil().format(DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH)))
                    .append("</b>\n\n");
        }
        return builder.toString().trim();
    }

    private String compactDraft(ProfileDraft draft) {
        StringBuilder builder = new StringBuilder();
        builder.append("Gender: ").append(labelOrDash(draft.getGender() == null ? null : draft.getGender().label())).append("\n");
        builder.append("Looking for: ").append(labelOrDash(draft.getLookingFor() == null ? null : draft.getLookingFor().label())).append("\n");
        builder.append("Goal: ").append(labelOrDash(draft.getGoal() == null ? null : draft.getGoal().label())).append("\n");
        builder.append("Name: ").append(labelOrDash(draft.getName())).append("\n");
        builder.append("About: ").append(labelOrDash(draft.getAbout())).append("\n");
        builder.append("Age: ").append(draft.getAge() == null ? "—" : draft.getAge()).append("\n");
        builder.append("Preferred age: ");
        if (draft.getPreferredAgeMin() == null || draft.getPreferredAgeMax() == null) {
            builder.append("—\n");
        } else {
            builder.append(draft.getPreferredAgeMin()).append("-").append(draft.getPreferredAgeMax()).append("\n");
        }
        builder.append("Privacy: ").append(labelOrDash(draft.getPrivacyMode() == null ? null : draft.getPrivacyMode().label())).append("\n");
        builder.append("Country: ").append(
                draft.getCountryName() == null ? "—" : Htmls.escape(draft.getCountryFlag() + " " + draft.getCountryName())
        ).append("\n");
        builder.append("Media: ").append(draft.getMedia().size()).append(" item(s)");
        return builder.toString();
    }

    private String labelOrDash(String value) {
        return value == null || value.isBlank() ? "—" : Htmls.escape(value);
    }

    private String displayName(UserAccount account) {
        return account.getFirstName() == null || account.getFirstName().isBlank() ? "there" : account.getFirstName();
    }
}
