package com.videocharter.bot;

import com.videocharter.config.BotConfig;
import com.videocharter.model.AdsgramAd;
import com.videocharter.model.Country;
import com.videocharter.model.DomainEnums;
import com.videocharter.model.DomainEnums.Gender;
import com.videocharter.model.DomainEnums.Goal;
import com.videocharter.model.DomainEnums.MediaType;
import com.videocharter.model.DomainEnums.PartnerPreference;
import com.videocharter.model.DomainEnums.PrivacyMode;
import com.videocharter.model.MediaAttachment;
import com.videocharter.model.ProfileDraft;
import com.videocharter.model.ReportRecord;
import com.videocharter.model.UserAccount;
import com.videocharter.model.UserProfile;
import com.videocharter.service.CountryCatalog;
import com.videocharter.service.DailyQuotaService;
import com.videocharter.service.AdsgramService;
import com.videocharter.service.ProfileService;
import com.videocharter.service.ProfileService.SubscriptionPricing;
import com.videocharter.service.ProfileService.SubscriptionView;
import com.videocharter.service.RateLimiterService;
import com.videocharter.service.SessionService;
import com.videocharter.service.SessionService.ExpectedInput;
import com.videocharter.service.SessionService.UserSession;
import com.videocharter.service.SessionService.UserSession.ProfileScreenContext;
import com.videocharter.ui.UiFactory;
import com.videocharter.ui.UiFactory.ButtonSpec;
import com.videocharter.util.Htmls;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class VideoCharterBot extends TelegramLongPollingBot {

    private static final Pattern AGE_RANGE_PATTERN = Pattern.compile("\\s*(\\d{2})\\s*-\\s*(\\d{2})\\s*");
    private static final int COUNTRIES_PER_PAGE = 10;
    private static final int SUBSCRIPTIONS_PER_PAGE = 6;

    private final BotConfig config;
    private final ProfileService profileService;
    private final SessionService sessionService;
    private final RateLimiterService limiterService;
    private final AdsgramService adsgramService;
    private final UiFactory uiFactory;
    private final CountryCatalog countryCatalog;

    public VideoCharterBot(
            BotConfig config,
            ProfileService profileService,
            SessionService sessionService,
            RateLimiterService limiterService,
            AdsgramService adsgramService,
            UiFactory uiFactory,
            CountryCatalog countryCatalog
    ) {
        this.config = config;
        this.profileService = profileService;
        this.sessionService = sessionService;
        this.limiterService = limiterService;
        this.adsgramService = adsgramService;
        this.uiFactory = uiFactory;
        this.countryCatalog = countryCatalog;
    }

    @Override
    public String getBotUsername() {
        return config.username();
    }

    @Override
    public String getBotToken() {
        return config.token();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasPreCheckoutQuery()) {
                handlePreCheckout(update);
                return;
            }
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
                return;
            }
            if (update.hasMessage()) {
                handleMessage(update.getMessage());
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            if (update.hasMessage()) {
                showFallbackError(update.getMessage().getChatId(), userSession(update.getMessage().getFrom()));
            } else if (update.hasCallbackQuery()) {
                showFallbackError(update.getCallbackQuery().getMessage().getChatId(), userSession(update.getCallbackQuery().getFrom()));
            }
        }
    }

    private void handlePreCheckout(Update update) {
        AnswerPreCheckoutQuery answer = new AnswerPreCheckoutQuery();
        answer.setPreCheckoutQueryId(update.getPreCheckoutQuery().getId());
        answer.setOk(true);
        try {
            execute(answer);
        } catch (TelegramApiException exception) {
            throw new IllegalStateException("Unable to answer pre-checkout query", exception);
        }
    }

    private void handleMessage(Message message) {
        User user = message.getFrom();
        UserSession session = userSession(user);
        UserAccount account = profileService.touchUser(user.getId(), user.getUserName(), user.getFirstName());

        if (profileService.isBanned(user.getId())) {
            cleanupCardMessages(message.getChatId(), session);
            renderMenu(message.getChatId(), session, "<b>Access blocked</b>\nYour account is blocked by moderation.", keyboardHomeOnly());
            return;
        }

        if (message.getSuccessfulPayment() != null) {
            handleSuccessfulPayment(message, session);
            deleteIncomingMessage(message);
            return;
        }

        String text = message.getText();
        if (isTestCommand(text)) {
            session.setExpectedInput(ExpectedInput.NONE);
            session.setDraft(null);
            session.resetReportDraft();
            session.resetProfileScreen();
            cleanupCardMessages(message.getChatId(), session);
            deleteIncomingMessage(message);
            openTestAd(message.getChatId(), session, account);
            return;
        }
        if (text != null && ("/start".equals(text) || "/menu".equals(text))) {
            session.setExpectedInput(ExpectedInput.NONE);
            session.setDraft(null);
            session.resetReportDraft();
            session.resetProfileScreen();
            cleanupCardMessages(message.getChatId(), session);
            if (!"/start".equals(text)) {
                deleteIncomingMessage(message);
            }
            openHome(message.getChatId(), account, session, null);
            return;
        }

        if (session.getExpectedInput() != ExpectedInput.NONE) {
            handleExpectedInput(message, session, account);
            deleteIncomingMessage(message);
            return;
        }

        deleteIncomingMessage(message);
        openHome(message.getChatId(), account, session, "Use the inline buttons below.");
    }

    private boolean isTestCommand(String text) {
        if (text == null) {
            return false;
        }
        return "/test".equals(text) || text.startsWith("/test@");
    }

    private void handleSuccessfulPayment(Message message, UserSession session) {
        String payload = message.getSuccessfulPayment().getInvoicePayload();
        int days = payload.endsWith(":365") ? 365 : 30;
        profileService.applySubscription(message.getFrom().getId(), days, LocalDate.now());
        UserAccount refreshed = profileService.getAccount(message.getFrom().getId());
        openSubscription(message.getChatId(), session, refreshed, "Subscription activated.");
    }

    private void handleExpectedInput(Message message, UserSession session, UserAccount account) {
        switch (session.getExpectedInput()) {
            case NAME -> handleNameInput(message, session, account);
            case AGE -> handleAgeInput(message, session, account);
            case AGE_RANGE -> handleAgeRangeInput(message, session, account);
            case MEDIA_PHOTO -> handlePhotoInput(message, session, account);
            case MEDIA_VIDEO -> handleVideoInput(message, session, account);
            case REPORT_EVIDENCE -> handleReportEvidenceInput(message, session, account);
            case ADD_ADMIN_ID -> handleAdminInput(message, session, true, account);
            case REMOVE_ADMIN_ID -> handleAdminInput(message, session, false, account);
            case ADD_MODERATOR_ID -> handleModeratorInput(message, session, true, account);
            case REMOVE_MODERATOR_ID -> handleModeratorInput(message, session, false, account);
            case SUBSCRIPTION_PRICE -> handleSubscriptionPriceInput(message, session, account);
            case NONE -> {
            }
        }
    }

    private void handleNameInput(Message message, UserSession session, UserAccount account) {
        if (session.getDraft() == null) {
            openHome(message.getChatId(), account, session, "No active profile setup. Start again.");
            return;
        }
        String value = message.getText();
        if (value == null || value.isBlank() || value.length() > 32) {
            renderWizard(message.getChatId(), session, "Send a name between 1 and 32 characters.");
            return;
        }
        session.getDraft().setName(value.trim());
        session.getDraft().setStep(ProfileDraft.WizardStep.AGE);
        session.setExpectedInput(ExpectedInput.NONE);
        renderWizard(message.getChatId(), session, "Now send your age as a number.");
    }

    private void handleAgeInput(Message message, UserSession session, UserAccount account) {
        if (session.getDraft() == null) {
            openHome(message.getChatId(), account, session, "No active profile setup. Start again.");
            return;
        }
        try {
            int age = Integer.parseInt(Objects.requireNonNullElse(message.getText(), "").trim());
            if (age < 18 || age > 80) {
                throw new NumberFormatException("Out of range");
            }
            session.getDraft().setAge(age);
            session.getDraft().setStep(ProfileDraft.WizardStep.AGE_RANGE);
            session.setExpectedInput(ExpectedInput.NONE);
            renderWizard(message.getChatId(), session, "Send the preferred age range, for example <b>18-30</b>.");
        } catch (Exception ignored) {
            renderWizard(message.getChatId(), session, "Age must be a number between 18 and 80.");
        }
    }

    private void handleAgeRangeInput(Message message, UserSession session, UserAccount account) {
        if (session.getDraft() == null) {
            openHome(message.getChatId(), account, session, "No active profile setup. Start again.");
            return;
        }
        Matcher matcher = AGE_RANGE_PATTERN.matcher(Objects.requireNonNullElse(message.getText(), ""));
        if (!matcher.matches()) {
            renderWizard(message.getChatId(), session, "Use the format <b>18-30</b>.");
            return;
        }
        int min = Integer.parseInt(matcher.group(1));
        int max = Integer.parseInt(matcher.group(2));
        if (min < 18 || max > 80 || min > max) {
            renderWizard(message.getChatId(), session, "Preferred age must stay within 18-80 and the minimum must not be greater than the maximum.");
            return;
        }
        session.getDraft().setPreferredAgeMin(min);
        session.getDraft().setPreferredAgeMax(max);
        session.getDraft().setStep(ProfileDraft.WizardStep.PRIVACY);
        session.setExpectedInput(ExpectedInput.NONE);
        renderWizard(message.getChatId(), session, "Choose your privacy mode.");
    }

    private void handlePhotoInput(Message message, UserSession session, UserAccount account) {
        if (session.getDraft() == null) {
            openHome(message.getChatId(), account, session, "No active media editor. Start again.");
            return;
        }
        if (!message.hasPhoto()) {
            renderWizard(message.getChatId(), session, "Please send a photo.");
            return;
        }
        if (!session.getDraft().canAddPhoto()) {
            session.setExpectedInput(ExpectedInput.NONE);
            renderWizard(message.getChatId(), session, "Photo limit reached. Remove something first.");
            return;
        }
        String fileId = largestPhoto(message.getPhoto()).getFileId();
        session.getDraft().addPhoto(fileId);
        session.setExpectedInput(ExpectedInput.NONE);
        renderWizard(message.getChatId(), session, "Photo added.");
    }

    private void handleVideoInput(Message message, UserSession session, UserAccount account) {
        if (session.getDraft() == null) {
            openHome(message.getChatId(), account, session, "No active media editor. Start again.");
            return;
        }
        String fileId = extractVideoFileId(message);
        if (fileId == null) {
            renderWizard(message.getChatId(), session, "Please send a video.");
            return;
        }
        if (!session.getDraft().canAddVideo()) {
            session.setExpectedInput(ExpectedInput.NONE);
            renderWizard(message.getChatId(), session, "Video limit reached. You can keep at most 1 video.");
            return;
        }
        session.getDraft().addVideo(fileId);
        session.setExpectedInput(ExpectedInput.NONE);
        renderWizard(message.getChatId(), session, "Video added.");
    }

    private void handleReportEvidenceInput(Message message, UserSession session, UserAccount account) {
        if (session.getReportTargetUserId() == null || session.getReportReason() == null) {
            openHome(message.getChatId(), account, session, "No active report flow.");
            return;
        }
        if (message.hasText()) {
            session.setReportEvidenceText(message.getText().trim());
        } else if (message.hasPhoto()) {
            session.setReportEvidenceFileId(largestPhoto(message.getPhoto()).getFileId());
            session.setReportEvidenceFileType("photo");
        } else if (message.hasVideo()) {
            session.setReportEvidenceFileId(message.getVideo().getFileId());
            session.setReportEvidenceFileType("video");
        } else {
            renderReportEvidencePrompt(message.getChatId(), session);
            return;
        }
        session.setExpectedInput(ExpectedInput.NONE);
        renderReportConfirm(message.getChatId(), session);
    }

    private void handleModeratorInput(Message message, UserSession session, boolean add, UserAccount account) {
        try {
            long userId = Long.parseLong(Objects.requireNonNullElse(message.getText(), "").trim());
            profileService.setModerator(userId, add);
            session.setExpectedInput(ExpectedInput.NONE);
            openModeratorManagement(message.getChatId(), session, account, add ? "Moderator added." : "Moderator removed.");
        } catch (Exception ignored) {
            renderMenu(
                    message.getChatId(),
                    session,
                    add ? "<b>🛡 Add moderator</b>\nSend a numeric Telegram user ID." : "<b>🛡 Remove moderator</b>\nSend a numeric Telegram user ID.",
                    keyboardModeratorInput(add)
            );
        }
    }

    private void handleAdminInput(Message message, UserSession session, boolean add, UserAccount account) {
        try {
            long userId = Long.parseLong(Objects.requireNonNullElse(message.getText(), "").trim());
            profileService.setAdmin(userId, add);
            session.setExpectedInput(ExpectedInput.NONE);
            openModeratorManagement(message.getChatId(), session, account, add ? "Admin added." : "Admin removed.");
        } catch (Exception ignored) {
            renderMenu(
                    message.getChatId(),
                    session,
                    add ? "<b>👑 Add admin</b>\nSend a numeric Telegram user ID." : "<b>👑 Remove admin</b>\nSend a numeric Telegram user ID.",
                    keyboardAdminInput(add)
            );
        }
    }

    private void handleSubscriptionPriceInput(Message message, UserSession session, UserAccount account) {
        if (!profileService.isAdmin(account.getUserId()) || session.getPendingSubscriptionPriceDays() == null) {
            session.setExpectedInput(ExpectedInput.NONE);
            session.setPendingSubscriptionPriceDays(null);
            openModerationHome(message.getChatId(), session, account, "Admin pricing edit is unavailable.");
            return;
        }
        try {
            int stars = Integer.parseInt(Objects.requireNonNullElse(message.getText(), "").trim());
            if (stars <= 0 || stars > 100_000) {
                throw new NumberFormatException("Out of range");
            }
            int days = session.getPendingSubscriptionPriceDays();
            profileService.updateSubscriptionPrice(days, stars);
            session.setExpectedInput(ExpectedInput.NONE);
            session.setPendingSubscriptionPriceDays(null);
            openAdminPricing(message.getChatId(), session, account, "Price updated.");
        } catch (Exception ignored) {
            openAdminPricing(message.getChatId(), session, account, "Send a valid positive integer Stars price.");
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        User user = callbackQuery.getFrom();
        UserSession session = userSession(user);
        UserAccount account = profileService.touchUser(user.getId(), user.getUserName(), user.getFirstName());

        if (profileService.isBanned(user.getId())) {
            answerCallback(callbackQuery, "Blocked");
            cleanupCardMessages(callbackQuery.getMessage().getChatId(), session);
            renderMenu(callbackQuery.getMessage().getChatId(), session, "<b>Access blocked</b>\nYour account is blocked by moderation.", keyboardHomeOnly());
            return;
        }
        if (limiterService.isTooFast(user.getId())) {
            answerCallback(callbackQuery, "Slow down a bit.");
            return;
        }

        session.setMenuMessageId(callbackQuery.getMessage().getMessageId());
        String data = callbackQuery.getData();

        if ("noop".equals(data)) {
            answerCallback(callbackQuery, null);
            return;
        }

        if ("home".equals(data)) {
            answerCallback(callbackQuery, null);
            session.setExpectedInput(ExpectedInput.NONE);
            session.setDraft(null);
            session.resetReportDraft();
            session.resetProfileScreen();
            cleanupCardMessages(callbackQuery.getMessage().getChatId(), session);
            openHome(callbackQuery.getMessage().getChatId(), account, session, null);
            return;
        }

        if ("menu:create".equals(data)) {
            answerCallback(callbackQuery, null);
            startWizard(callbackQuery.getMessage().getChatId(), session, null);
            return;
        }
        if ("menu:profile".equals(data)) {
            answerCallback(callbackQuery, null);
            openMyProfile(callbackQuery.getMessage().getChatId(), session, account);
            return;
        }
        if ("menu:search".equals(data)) {
            answerCallback(callbackQuery, null);
            openSearch(callbackQuery.getMessage().getChatId(), session, account, true);
            return;
        }
        if ("menu:likes".equals(data)) {
            answerCallback(callbackQuery, null);
            openLikes(callbackQuery.getMessage().getChatId(), session, account);
            return;
        }
        if ("menu:ads".equals(data)) {
            answerCallback(callbackQuery, null);
            openSubscription(callbackQuery.getMessage().getChatId(), session, account, null);
            return;
        }
        if ("menu:moderation".equals(data)) {
            answerCallback(callbackQuery, null);
            openModerationHome(callbackQuery.getMessage().getChatId(), session, account, null);
            return;
        }

        if ("profile:rebuild".equals(data)) {
            answerCallback(callbackQuery, null);
            startWizard(callbackQuery.getMessage().getChatId(), session, profileService.getProfile(user.getId()));
            return;
        }
        if ("profile:media".equals(data)) {
            answerCallback(callbackQuery, null);
            UserProfile current = profileService.getProfile(user.getId());
            if (current == null) {
                openHome(callbackQuery.getMessage().getChatId(), account, session, "Create a profile first.");
                return;
            }
            ProfileDraft draft = ProfileDraft.fromProfile(current);
            draft.setStep(ProfileDraft.WizardStep.MEDIA);
            session.setDraft(draft);
            session.setExpectedInput(ExpectedInput.NONE);
            renderWizard(callbackQuery.getMessage().getChatId(), session, "Manage your media.");
            return;
        }
        if ("profile:delete".equals(data)) {
            answerCallback(callbackQuery, null);
            renderMenu(callbackQuery.getMessage().getChatId(), session, "<b>Delete profile</b>\nThis will remove your profile, likes and matches.", keyboardDeleteProfileConfirm());
            return;
        }
        if ("profile:delete:confirm".equals(data)) {
            answerCallback(callbackQuery, "Deleted");
            cleanupCardMessages(callbackQuery.getMessage().getChatId(), session);
            profileService.deleteProfile(user.getId());
            session.setDraft(null);
            openHome(callbackQuery.getMessage().getChatId(), account, session, "Your profile was deleted.");
            return;
        }

        if (data.startsWith("wizard:")) {
            answerCallback(callbackQuery, null);
            handleWizardCallback(callbackQuery.getMessage().getChatId(), session, account, data);
            return;
        }

        if (data.startsWith("media:")) {
            answerCallback(callbackQuery, null);
            handleProfileMediaCallback(callbackQuery.getMessage().getChatId(), session, account, data);
            return;
        }

        if (data.startsWith("browse:")) {
            handleBrowseCallback(callbackQuery, session, account, data);
            return;
        }

        if (data.startsWith("likes:")) {
            handleLikesCallback(callbackQuery, session, account, data);
            return;
        }

        if (data.startsWith("report:")) {
            answerCallback(callbackQuery, null);
            handleReportCallback(callbackQuery.getMessage().getChatId(), session, account, data);
            return;
        }

        if (data.startsWith("sub:buy:")) {
            answerCallback(callbackQuery, null);
            int days = Integer.parseInt(data.substring("sub:buy:".length()));
            sendSubscriptionInvoice(callbackQuery.getMessage().getChatId(), session, days);
            return;
        }

        if (data.startsWith("mod:")) {
            answerCallback(callbackQuery, null);
            handleModerationCallback(callbackQuery.getMessage().getChatId(), session, account, data);
        }
    }

    private void handleWizardCallback(long chatId, UserSession session, UserAccount account, String data) {
        String[] parts = data.split(":");
        if ("cancel".equals(parts[1])) {
            session.setDraft(null);
            session.setExpectedInput(ExpectedInput.NONE);
            openHome(chatId, account, session, "Profile setup cancelled.");
            return;
        }
        if ("back".equals(parts[1])) {
            goWizardBack(chatId, session);
            return;
        }
        if ("gender".equals(parts[1])) {
            session.getDraft().setGender(Gender.valueOf(parts[2]));
            session.getDraft().setStep(ProfileDraft.WizardStep.LOOKING_FOR);
            renderWizard(chatId, session, "Choose who you want to meet.");
            return;
        }
        if ("looking".equals(parts[1])) {
            session.getDraft().setLookingFor(PartnerPreference.valueOf(parts[2]));
            session.getDraft().setStep(ProfileDraft.WizardStep.GOAL);
            renderWizard(chatId, session, "Pick your goal.");
            return;
        }
        if ("goal".equals(parts[1])) {
            session.getDraft().setGoal(Goal.valueOf(parts[2]));
            session.getDraft().setStep(ProfileDraft.WizardStep.NAME);
            session.setExpectedInput(ExpectedInput.NAME);
            renderWizard(chatId, session, "Send your name.");
            return;
        }
        if ("privacy".equals(parts[1])) {
            session.getDraft().setPrivacyMode(PrivacyMode.valueOf(parts[2]));
            session.getDraft().setStep(ProfileDraft.WizardStep.COUNTRY);
            session.setCountryPage(0);
            renderWizard(chatId, session, "Choose your country.");
            return;
        }
        if ("countryPage".equals(parts[1])) {
            session.setCountryPage(Integer.parseInt(parts[2]));
            session.getDraft().setStep(ProfileDraft.WizardStep.COUNTRY);
            renderWizard(chatId, session, "Choose your country.");
            return;
        }
        if ("country".equals(parts[1])) {
            Optional<Country> country = profileService.findCountry(parts[2]);
            if (country.isEmpty()) {
                renderWizard(chatId, session, "Country not found.");
                return;
            }
            session.getDraft().setCountryCode(country.get().code());
            session.getDraft().setCountryName(country.get().name());
            session.getDraft().setCountryFlag(country.get().flag());
            session.getDraft().setStep(ProfileDraft.WizardStep.MEDIA);
            renderWizard(chatId, session, "Add up to 3 photos, or 1 video plus 2 photos.");
            return;
        }
        if ("mediaPhoto".equals(parts[1])) {
            session.setExpectedInput(ExpectedInput.MEDIA_PHOTO);
            session.getDraft().setStep(ProfileDraft.WizardStep.MEDIA);
            renderWizard(chatId, session, "Send a photo now.");
            return;
        }
        if ("mediaVideo".equals(parts[1])) {
            session.setExpectedInput(ExpectedInput.MEDIA_VIDEO);
            session.getDraft().setStep(ProfileDraft.WizardStep.MEDIA);
            renderWizard(chatId, session, "Send a video now.");
            return;
        }
        if ("mediaRemove".equals(parts[1])) {
            session.getDraft().removeLastMedia();
            session.setExpectedInput(ExpectedInput.NONE);
            renderWizard(chatId, session, "Removed the last media item.");
            return;
        }
        if ("mediaClear".equals(parts[1])) {
            session.getDraft().clearMedia();
            session.setExpectedInput(ExpectedInput.NONE);
            renderWizard(chatId, session, "Media cleared.");
            return;
        }
        if ("save".equals(parts[1])) {
            if (session.getDraft().getMedia().isEmpty()) {
                renderWizard(chatId, session, "Add at least one media item before saving.");
                return;
            }
            profileService.saveProfile(account.getUserId(), account.getUsername(), session.getDraft());
            session.setDraft(null);
            session.setExpectedInput(ExpectedInput.NONE);
            openMyProfile(chatId, session, account);
        }
    }

    private void goWizardBack(long chatId, UserSession session) {
        if (session.getDraft() == null) {
            return;
        }
        session.setExpectedInput(ExpectedInput.NONE);
        ProfileDraft.WizardStep step = session.getDraft().getStep();
        switch (step) {
            case GENDER -> session.setDraft(null);
            case LOOKING_FOR -> session.getDraft().setStep(ProfileDraft.WizardStep.GENDER);
            case GOAL -> session.getDraft().setStep(ProfileDraft.WizardStep.LOOKING_FOR);
            case NAME -> session.getDraft().setStep(ProfileDraft.WizardStep.GOAL);
            case AGE -> session.getDraft().setStep(ProfileDraft.WizardStep.NAME);
            case AGE_RANGE -> session.getDraft().setStep(ProfileDraft.WizardStep.AGE);
            case PRIVACY -> session.getDraft().setStep(ProfileDraft.WizardStep.AGE_RANGE);
            case COUNTRY -> session.getDraft().setStep(ProfileDraft.WizardStep.PRIVACY);
            case MEDIA -> session.getDraft().setStep(ProfileDraft.WizardStep.COUNTRY);
        }
        if (session.getDraft() == null) {
            renderMenu(chatId, session, "<b>Profile setup</b>\nCancelled.", keyboardHomeOnly());
            return;
        }
        renderWizard(chatId, session, null);
    }

    private void handleProfileMediaCallback(long chatId, UserSession session, UserAccount account, String data) {
        Long profileUserId = session.getCurrentScreenProfileUserId();
        ProfileScreenContext context = session.getProfileScreenContext();
        if (profileUserId == null || context == ProfileScreenContext.NONE) {
            openHome(chatId, account, session, "The current card is no longer active.");
            return;
        }

        UserProfile profile = profileService.getProfile(profileUserId);
        if (profile == null || profile.getMedia() == null || profile.getMedia().size() <= 1) {
            rerenderProfileContext(chatId, session, account);
            return;
        }

        int mediaCount = profile.getMedia().size();
        int currentIndex = Math.max(0, Math.min(session.getCurrentMediaIndex(), mediaCount - 1));
        if ("media:prev".equals(data)) {
            session.setCurrentMediaIndex((currentIndex - 1 + mediaCount) % mediaCount);
        } else if ("media:next".equals(data)) {
            session.setCurrentMediaIndex((currentIndex + 1) % mediaCount);
        }
        rerenderProfileContext(chatId, session, account);
    }

    private void rerenderProfileContext(long chatId, UserSession session, UserAccount account) {
        ProfileScreenContext context = session.getProfileScreenContext();
        Long profileUserId = session.getCurrentScreenProfileUserId();
        if (profileUserId == null || context == ProfileScreenContext.NONE) {
            openHome(chatId, account, session, null);
            return;
        }

        UserProfile profile = profileService.getProfile(profileUserId);
        if (profile == null) {
            openHome(chatId, account, session, "This profile is no longer available.");
            return;
        }

        switch (context) {
            case MY_PROFILE -> renderProfileScreen(
                    chatId,
                    session,
                    profile,
                    uiFactory.profileSummary(profile, true),
                    keyboardMyProfile(),
                    ProfileScreenContext.MY_PROFILE,
                    true
            );
            case BROWSE -> renderProfileScreen(
                    chatId,
                    session,
                    profile,
                    uiFactory.browseCard(profile),
                    keyboardBrowse(),
                    ProfileScreenContext.BROWSE,
                    true
            );
            case LIKES -> renderProfileScreen(
                    chatId,
                    session,
                    profile,
                    "<b>Someone likes you</b>\n\n" + uiFactory.browseCard(profile),
                    keyboardLikes(),
                    ProfileScreenContext.LIKES,
                    true
            );
            case MODERATION_REPORT -> {
                List<ProfileService.ModerationReportView> reports = profileService.getOpenReports();
                if (reports.isEmpty()) {
                    renderMenu(chatId, session, "<b>No open reports</b>\nEverything is clean right now.", keyboardModerationBack());
                    return;
                }
                int normalized = Math.max(0, Math.min(session.getModerationIndex(), reports.size() - 1));
                ProfileService.ModerationReportView view = reports.get(normalized);
                if (view.target() == null) {
                    renderMenu(chatId, session, uiFactory.moderationReportText(view.report(), view.reporter(), view.target()), keyboardReportModeration(view.report().getId(), normalized, reports.size()));
                    return;
                }
                renderProfileScreen(
                        chatId,
                        session,
                        view.target(),
                        uiFactory.moderationReportText(view.report(), view.reporter(), view.target()),
                        keyboardReportModeration(view.report().getId(), normalized, reports.size()),
                        ProfileScreenContext.MODERATION_REPORT,
                        true
                );
            }
            case NONE -> openHome(chatId, account, session, null);
        }
    }

    private void handleBrowseCallback(CallbackQuery callbackQuery, UserSession session, UserAccount account, String data) {
        long chatId = callbackQuery.getMessage().getChatId();
        Long currentProfileId = session.getCurrentBrowseProfileId();
        switch (data) {
            case "browse:like" -> {
                answerCallback(callbackQuery, "Liked");
                if (currentProfileId == null) {
                    openSearch(chatId, session, account, false);
                    return;
                }
                ProfileService.LikeResult result = profileService.likeProfile(account.getUserId(), currentProfileId);
                if (result.mutual()) {
                    sendMatchNotifications(account.getUserId(), currentProfileId);
                }
                showNextBrowseCandidate(chatId, session, account, true);
            }
            case "browse:skip" -> {
                answerCallback(callbackQuery, "Skipped");
                showNextBrowseCandidate(chatId, session, account, true);
            }
            case "browse:back" -> {
                if (session.getBrowseHistory().isEmpty()) {
                    answerCallback(callbackQuery, "No previous profile.");
                    return;
                }
                answerCallback(callbackQuery, null);
                Long previousId = session.getBrowseHistory().pop();
                UserProfile previous = profileService.getProfile(previousId);
                if (previous == null) {
                    showNextBrowseCandidate(chatId, session, account, false);
                    return;
                }
                showBrowseProfile(chatId, session, previous, false);
            }
            case "browse:report" -> {
                answerCallback(callbackQuery, null);
                if (currentProfileId == null) {
                    openSearch(chatId, session, account, false);
                    return;
                }
                session.setReportTargetUserId(currentProfileId);
                session.setReportReason(null);
                session.setExpectedInput(ExpectedInput.NONE);
                renderReportReason(chatId, session);
            }
            case "browse:continueAd" -> {
                answerCallback(callbackQuery, null);
                Long pending = session.getPendingProfileAfterAd();
                if (pending == null) {
                    showNextBrowseCandidate(chatId, session, account, false);
                    return;
                }
                UserProfile profile = profileService.getProfile(pending);
                session.setPendingProfileAfterAd(null);
                if (profile == null) {
                    showNextBrowseCandidate(chatId, session, account, true);
                    return;
                }
                showBrowseProfile(chatId, session, profile, true);
            }
            default -> answerCallback(callbackQuery, null);
        }
    }

    private void handleLikesCallback(CallbackQuery callbackQuery, UserSession session, UserAccount account, String data) {
        long chatId = callbackQuery.getMessage().getChatId();
        Long currentProfileId = session.getCurrentBrowseProfileId();
        if (currentProfileId == null) {
            answerCallback(callbackQuery, null);
            openLikes(chatId, session, account);
            return;
        }
        switch (data) {
            case "likes:back" -> {
                answerCallback(callbackQuery, "Matched");
                profileService.likeProfile(account.getUserId(), currentProfileId);
                sendMatchNotifications(account.getUserId(), currentProfileId);
                openLikes(chatId, session, account);
            }
            case "likes:pass" -> {
                answerCallback(callbackQuery, "Passed");
                profileService.dismissIncomingLike(account.getUserId(), currentProfileId);
                openLikes(chatId, session, account);
            }
            default -> answerCallback(callbackQuery, null);
        }
    }

    private void handleReportCallback(long chatId, UserSession session, UserAccount account, String data) {
        String[] parts = data.split(":");
        if ("reason".equals(parts[1])) {
            session.setReportReason(DomainEnums.ReportReason.valueOf(parts[2]));
            session.setExpectedInput(ExpectedInput.REPORT_EVIDENCE);
            renderReportEvidencePrompt(chatId, session);
            return;
        }
        if ("skipEvidence".equals(parts[1])) {
            session.setExpectedInput(ExpectedInput.NONE);
            renderReportConfirm(chatId, session);
            return;
        }
        if ("confirm".equals(parts[1])) {
            profileService.createReport(
                    account.getUserId(),
                    session.getReportTargetUserId(),
                    session.getReportReason(),
                    session.getReportEvidenceText(),
                    session.getReportEvidenceFileId(),
                    session.getReportEvidenceFileType()
            );
            session.resetReportDraft();
            session.setExpectedInput(ExpectedInput.NONE);
            showNextBrowseCandidate(chatId, session, account, true);
            return;
        }
        if ("cancel".equals(parts[1])) {
            session.resetReportDraft();
            session.setExpectedInput(ExpectedInput.NONE);
            Long current = session.getCurrentBrowseProfileId();
            UserProfile profile = current == null ? null : profileService.getProfile(current);
            if (profile == null) {
                openSearch(chatId, session, account, false);
                return;
            }
            showBrowseProfile(chatId, session, profile, false);
        }
    }

    private void handleModerationCallback(long chatId, UserSession session, UserAccount account, String data) {
        if (!profileService.isModerator(account.getUserId())) {
            openHome(chatId, account, session, "Moderation access is unavailable.");
            return;
        }
        if ("mod:reports".equals(data)) {
            openModerationReport(chatId, session, 0);
            return;
        }
        if ("mod:stats".equals(data)) {
            openAdminOverview(chatId, session, account, null);
            return;
        }
        if ("mod:pricing".equals(data)) {
            openAdminPricing(chatId, session, account, null);
            return;
        }
        if ("mod:subscriptions".equals(data)) {
            openAdminSubscriptions(chatId, session, account, 0);
            return;
        }
        if ("mod:home".equals(data)) {
            openModerationHome(chatId, session, account, null);
            return;
        }
        if ("mod:mods".equals(data)) {
            openModeratorManagement(chatId, session, account, null);
            return;
        }
        if ("mod:add".equals(data)) {
            session.setExpectedInput(ExpectedInput.ADD_MODERATOR_ID);
            renderMenu(chatId, session, "<b>🛡 Add moderator</b>\nSend a numeric Telegram user ID.", keyboardModeratorInput(true));
            return;
        }
        if ("mod:remove".equals(data)) {
            session.setExpectedInput(ExpectedInput.REMOVE_MODERATOR_ID);
            renderMenu(chatId, session, "<b>🛡 Remove moderator</b>\nSend a numeric Telegram user ID.", keyboardModeratorInput(false));
            return;
        }
        if ("mod:addAdmin".equals(data)) {
            session.setExpectedInput(ExpectedInput.ADD_ADMIN_ID);
            renderMenu(chatId, session, "<b>👑 Add admin</b>\nSend a numeric Telegram user ID.", keyboardAdminInput(true));
            return;
        }
        if ("mod:removeAdmin".equals(data)) {
            session.setExpectedInput(ExpectedInput.REMOVE_ADMIN_ID);
            renderMenu(chatId, session, "<b>👑 Remove admin</b>\nSend a numeric Telegram user ID.", keyboardAdminInput(false));
            return;
        }
        if (data.startsWith("mod:report:")) {
            String[] parts = data.split(":");
            if ("next".equals(parts[2])) {
                openModerationReport(chatId, session, session.getModerationIndex() + 1);
                return;
            }
            if ("prev".equals(parts[2])) {
                openModerationReport(chatId, session, session.getModerationIndex() - 1);
                return;
            }
            if ("approve".equals(parts[2]) || "reject".equals(parts[2]) || "ban".equals(parts[2])) {
                long reportId = Long.parseLong(parts[3]);
                boolean approved = !"reject".equals(parts[2]);
                boolean ban = "ban".equals(parts[2]);
                profileService.resolveReport(reportId, account.getUserId(), approved, ban);
                openModerationReport(chatId, session, session.getModerationIndex());
            }
            return;
        }
        if (data.startsWith("mod:subs:page:")) {
            int page = Integer.parseInt(data.substring("mod:subs:page:".length()));
            openAdminSubscriptions(chatId, session, account, page);
            return;
        }
        if (data.startsWith("mod:price:set:")) {
            if (!profileService.isAdmin(account.getUserId())) {
                openModerationHome(chatId, session, account, "Only admins can change subscription pricing.");
                return;
            }
            int days = Integer.parseInt(data.substring("mod:price:set:".length()));
            session.setPendingSubscriptionPriceDays(days);
            session.setExpectedInput(ExpectedInput.SUBSCRIPTION_PRICE);
            openAdminPricing(chatId, session, account, "Send the new Stars price for the " + days + "-day plan.");
        }
    }

    private void openHome(long chatId, UserAccount account, UserSession session, String notice) {
        session.setExpectedInput(ExpectedInput.NONE);
        session.resetReportDraft();
        session.setPendingSubscriptionPriceDays(null);
        session.resetProfileScreen();
        boolean hasProfile = profileService.hasProfile(account.getUserId());
        int likesCount = profileService.countIncomingLikes(account.getUserId());
        boolean adsDisabled = profileService.hasActiveSubscription(account.getUserId(), LocalDate.now());
        boolean moderationEnabled = profileService.isModerator(account.getUserId());
        String prefix = notice == null ? "" : notice + "\n\n";
        renderMenu(chatId, session, prefix + uiFactory.homeText(account, hasProfile, likesCount, adsDisabled, moderationEnabled), keyboardHome(hasProfile, moderationEnabled));
    }

    private void openMyProfile(long chatId, UserSession session, UserAccount account) {
        cleanupCardMessages(chatId, session);
        session.resetProfileScreen();
        UserProfile profile = profileService.getProfile(account.getUserId());
        if (profile == null) {
            renderMenu(chatId, session, "<b>No profile yet</b>\nCreate one to start browsing.", keyboardNoProfile());
            return;
        }
        renderProfileScreen(chatId, session, profile, uiFactory.profileSummary(profile, true), keyboardMyProfile(), ProfileScreenContext.MY_PROFILE, false);
    }

    private void startWizard(long chatId, UserSession session, UserProfile currentProfile) {
        cleanupCardMessages(chatId, session);
        session.resetReportDraft();
        session.setExpectedInput(ExpectedInput.NONE);
        session.resetProfileScreen();
        session.setDraft(currentProfile == null ? ProfileDraft.empty() : ProfileDraft.fromProfile(currentProfile));
        session.getDraft().setStep(ProfileDraft.WizardStep.GENDER);
        renderWizard(chatId, session, "Let’s build your profile.");
    }

    private void renderWizard(long chatId, UserSession session, String extra) {
        ProfileDraft draft = session.getDraft();
        if (draft == null) {
            renderMenu(chatId, session, "<b>Profile setup</b>\nNo active draft.", keyboardHomeOnly());
            return;
        }
        draft.setStep(Objects.requireNonNullElse(draft.getStep(), ProfileDraft.WizardStep.GENDER));

        switch (draft.getStep()) {
            case GENDER -> renderMenu(chatId, session, uiFactory.wizardText(draft, extra == null ? "🧍 Choose your gender." : extra), keyboardGender(draft));
            case LOOKING_FOR -> renderMenu(chatId, session, uiFactory.wizardText(draft, extra == null ? "💞 Who are you looking for?" : extra), keyboardLookingFor(draft));
            case GOAL -> renderMenu(chatId, session, uiFactory.wizardText(draft, extra == null ? "🎯 Choose your goal." : extra), keyboardGoal(draft));
            case NAME -> {
                session.setExpectedInput(ExpectedInput.NAME);
                renderMenu(chatId, session, uiFactory.wizardText(draft, extra == null ? "🪪 Send your name." : extra), keyboardWizardBack());
            }
            case AGE -> {
                session.setExpectedInput(ExpectedInput.AGE);
                renderMenu(chatId, session, uiFactory.wizardText(draft, extra == null ? "🎂 Send your age." : extra), keyboardWizardBack());
            }
            case AGE_RANGE -> {
                session.setExpectedInput(ExpectedInput.AGE_RANGE);
                renderMenu(chatId, session, uiFactory.wizardText(draft, extra == null ? "📏 Send preferred age as 18-30." : extra), keyboardWizardBack());
            }
            case PRIVACY -> renderMenu(chatId, session, uiFactory.wizardText(draft, extra == null ? "🔒 Choose privacy mode." : extra), keyboardPrivacy(draft));
            case COUNTRY -> renderCountryPage(chatId, session, extra);
            case MEDIA -> renderMenu(chatId, session, uiFactory.wizardText(draft, extra == null ? "🖼 Manage your media." : extra), keyboardMedia(draft));
        }
    }

    private void renderCountryPage(long chatId, UserSession session, String extra) {
        List<Country> countries = profileService.getSortedCountries();
        int totalPages = Math.max(1, (countries.size() + COUNTRIES_PER_PAGE - 1) / COUNTRIES_PER_PAGE);
        int page = Math.max(0, Math.min(session.getCountryPage(), totalPages - 1));
        session.setCountryPage(page);

        int fromIndex = page * COUNTRIES_PER_PAGE;
        int toIndex = Math.min(fromIndex + COUNTRIES_PER_PAGE, countries.size());
        List<List<ButtonSpec>> rows = new ArrayList<>();
        for (int index = fromIndex; index < toIndex; index += 2) {
            List<ButtonSpec> row = new ArrayList<>();
            Country first = countries.get(index);
            row.add(ButtonSpec.callback(first.flag() + " " + first.name(), "wizard:country:" + first.code()));
            if (index + 1 < toIndex) {
                Country second = countries.get(index + 1);
                row.add(ButtonSpec.callback(second.flag() + " " + second.name(), "wizard:country:" + second.code()));
            }
            rows.add(row);
        }
        List<ButtonSpec> pager = new ArrayList<>();
        if (page > 0) {
            pager.add(ButtonSpec.callback("← Prev", "wizard:countryPage:" + (page - 1)));
        }
        pager.add(ButtonSpec.callback((page + 1) + "/" + totalPages, "noop"));
        if (page < totalPages - 1) {
            pager.add(ButtonSpec.callback("Next →", "wizard:countryPage:" + (page + 1)));
        }
        rows.add(pager);
        rows.add(List.of(ButtonSpec.callback("← Back", "wizard:back"), ButtonSpec.callback("Cancel", "wizard:cancel")));
        renderMenu(chatId, session, uiFactory.wizardText(session.getDraft(), (extra == null ? "🌍 Choose your country." : extra) + "\nPopular countries move up automatically."), uiFactory.keyboard(rows));
    }

    private void openSearch(long chatId, UserSession session, UserAccount account, boolean reset) {
        if (!profileService.hasProfile(account.getUserId())) {
            openHome(chatId, account, session, "Create a profile first.");
            return;
        }
        if (reset) {
            session.resetBrowsing();
        }
        showNextBrowseCandidate(chatId, session, account, true);
    }

    private void showNextBrowseCandidate(long chatId, UserSession session, UserAccount account, boolean countQuota) {
        List<UserProfile> candidates = profileService.findBrowseCandidates(account.getUserId(), session.getSeenProfileIds(), 20);
        if (candidates.isEmpty() && !session.getSeenProfileIds().isEmpty()) {
            session.getSeenProfileIds().clear();
            candidates = profileService.findBrowseCandidates(account.getUserId(), session.getSeenProfileIds(), 20);
        }
        if (candidates.isEmpty()) {
            cleanupCardMessages(chatId, session);
            session.setCurrentBrowseProfileId(null);
            renderMenu(chatId, session, "<b>No profiles right now</b>\nTry again later or adjust your profile.", keyboardAfterBrowseEmpty());
            return;
        }

        UserProfile next = candidates.getFirst();
        if (countQuota && !profileService.hasActiveSubscription(account.getUserId(), LocalDate.now())) {
            DailyQuotaService.ViewingDecision decision = profileService.registerProfileView(account.getUserId(), LocalDate.now());
            if (decision.adRequired()) {
                cleanupCardMessages(chatId, session);
                session.setPendingProfileAfterAd(next.getUserId());
                if (!renderAdsgramInterstitial(chatId, session, account.getUserId(), decision)) {
                    renderMenu(chatId, session, uiFactory.adInterstitialText(decision.freeLimit(), decision.viewedToday()), keyboardAdInterstitial());
                }
                return;
            }
        }
        showBrowseProfile(chatId, session, next, true);
    }

    private boolean renderAdsgramInterstitial(long chatId,
                                              UserSession session,
                                              long telegramUserId,
                                              DailyQuotaService.ViewingDecision decision) {
        Optional<AdsgramAd> maybeAd = adsgramService.pickBestAd(telegramUserId, null);
        if (maybeAd.isEmpty()) {
            return false;
        }

        AdsgramAd ad = maybeAd.get();
        StringBuilder builder = new StringBuilder();
        builder.append("<b>📣 Sponsored</b>\n\n");
        builder.append(ad.getTextHtml()).append("\n\n");
        builder.append("Viewed today: <b>").append(decision.viewedToday()).append("</b>\n");
        builder.append("Free limit today: <b>").append(decision.freeLimit()).append("</b>\n");
        builder.append("You can continue to the next profile at any time.");

        InlineKeyboardMarkup keyboard = keyboardAdsgramInterstitial(ad);
        if (isHttpUrl(ad.getImageUrl())) {
            renderProtectedPhotoScreen(chatId, session, ad.getImageUrl(), trimCaption(builder.toString()), keyboard);
            return true;
        }

        renderProtectedTextScreen(chatId, session, builder.toString(), keyboard);
        return true;
    }

    private void openTestAd(long chatId, UserSession session, UserAccount account) {
        Optional<AdsgramAd> maybeAd = adsgramService.pickBestAd(account.getUserId(), null);
        if (maybeAd.isEmpty()) {
            renderMenu(chatId, session, "<b>📣 Test ad</b>\nNo ad was returned by Adsgram right now. Try again in a moment.", keyboardHomeOnly());
            return;
        }

        AdsgramAd ad = maybeAd.get();
        String text = "<b>📣 Test ad</b>\n\n" + ad.getTextHtml() + "\n\nThis preview was requested manually with <code>/test</code>.";
        InlineKeyboardMarkup keyboard = keyboardAdsgramTest(ad);
        if (isHttpUrl(ad.getImageUrl())) {
            renderProtectedPhotoScreen(chatId, session, ad.getImageUrl(), trimCaption(text), keyboard);
            return;
        }
        renderProtectedTextScreen(chatId, session, text, keyboard);
    }

    private void showBrowseProfile(long chatId, UserSession session, UserProfile profile, boolean pushHistory) {
        cleanupCardMessages(chatId, session);
        if (pushHistory && session.getCurrentBrowseProfileId() != null && !session.getCurrentBrowseProfileId().equals(profile.getUserId())) {
            session.getBrowseHistory().push(session.getCurrentBrowseProfileId());
        }
        session.setCurrentBrowseProfileId(profile.getUserId());
        session.getSeenProfileIds().add(profile.getUserId());
        renderProfileScreen(chatId, session, profile, uiFactory.browseCard(profile), keyboardBrowse(), ProfileScreenContext.BROWSE, false);
    }

    private void openLikes(long chatId, UserSession session, UserAccount account) {
        if (!profileService.hasProfile(account.getUserId())) {
            openHome(chatId, account, session, "Create a profile first.");
            return;
        }
        cleanupCardMessages(chatId, session);
        List<UserProfile> incomingLikes = profileService.getIncomingLikes(account.getUserId());
        if (incomingLikes.isEmpty()) {
            renderMenu(chatId, session, "<b>No likes yet</b>\nWhen someone likes your profile, they will appear here.", keyboardHomeOnly());
            return;
        }
        UserProfile profile = incomingLikes.getFirst();
        session.setCurrentBrowseProfileId(profile.getUserId());
        renderProfileScreen(chatId, session, profile, "<b>Someone likes you</b>\n\n" + uiFactory.browseCard(profile), keyboardLikes(), ProfileScreenContext.LIKES, false);
    }

    private void renderReportReason(long chatId, UserSession session) {
        List<List<ButtonSpec>> rows = new ArrayList<>();
        rows.add(List.of(ButtonSpec.callback("Spam", "report:reason:SPAM"), ButtonSpec.callback("Fake", "report:reason:FAKE_PROFILE")));
        rows.add(List.of(ButtonSpec.callback("Inappropriate", "report:reason:INAPPROPRIATE_CONTENT"), ButtonSpec.callback("Underage", "report:reason:UNDERAGE")));
        rows.add(List.of(ButtonSpec.callback("Harassment", "report:reason:HARASSMENT"), ButtonSpec.callback("Other", "report:reason:OTHER")));
        rows.add(List.of(ButtonSpec.callback("Back to profile", "report:cancel")));
        renderMenu(chatId, session, "<b>Report profile</b>\nChoose a reason.", uiFactory.keyboard(rows));
    }

    private void renderReportEvidencePrompt(long chatId, UserSession session) {
        renderMenu(chatId, session, "<b>Report evidence</b>\nSend text, photo or video. Or skip if the reason is obvious.", keyboardReportEvidence());
    }

    private void renderReportConfirm(long chatId, UserSession session) {
        StringBuilder builder = new StringBuilder("<b>Confirm report</b>\n");
        builder.append("Reason: <b>").append(session.getReportReason().label()).append("</b>\n");
        if (session.getReportEvidenceText() != null) {
            builder.append("Text evidence added.\n");
        }
        if (session.getReportEvidenceFileId() != null) {
            builder.append("Media evidence added.\n");
        }
        builder.append("\nSend this report?");
        renderMenu(chatId, session, builder.toString(), keyboardReportConfirm());
    }

    private void openSubscription(long chatId, UserSession session, UserAccount account, String notice) {
        session.resetProfileScreen();
        SubscriptionPricing pricing = profileService.getSubscriptionPricing();
        String text = notice == null
                ? uiFactory.subscriptionText(account, pricing)
                : notice + "\n\n" + uiFactory.subscriptionText(account, pricing);
        renderMenu(chatId, session, text, keyboardSubscription());
    }

    private void sendSubscriptionInvoice(long chatId, UserSession session, int days) {
        SubscriptionPricing pricing = profileService.getSubscriptionPricing();
        int stars = days == 365 ? pricing.yearlyStars() : pricing.monthlyStars();
        SendInvoice invoice = new StarsInvoice();
        invoice.setChatId(chatId);
        invoice.setTitle("Disable ads");
        invoice.setDescription(days == 365 ? "Disable ads for 365 days." : "Disable ads for 30 days.");
        invoice.setPayload("subscription:" + days);
        invoice.setProviderToken("");
        invoice.setCurrency("XTR");
        invoice.setStartParameter("videocharter-subscription");
        invoice.setPrices(List.of(new LabeledPrice("Ad-free plan", stars)));
        try {
            clearSubscriptionInvoiceMessage(chatId, session);
            Message sent = execute(invoice);
            session.setSubscriptionInvoiceMessageId(sent.getMessageId());
        } catch (TelegramApiException exception) {
            throw new IllegalStateException("Unable to send subscription invoice", exception);
        }
    }

    private void openModerationHome(long chatId, UserSession session, UserAccount account, String notice) {
        if (!profileService.isModerator(account.getUserId())) {
            openHome(chatId, account, session, "Moderation access is unavailable.");
            return;
        }
        session.resetProfileScreen();
        List<ProfileService.ModerationReportView> reports = profileService.getOpenReports();
        StringBuilder builder = new StringBuilder();
        if (notice != null) {
            builder.append(notice).append("\n\n");
        }
        builder.append("<b>Moderation</b>\n");
        builder.append("Open reports: <b>").append(reports.size()).append("</b>\n");
        builder.append("Use inline actions to review reports");
        if (profileService.isAdmin(account.getUserId())) {
            builder.append(", manage moderators and subscription settings");
        }
        builder.append(".");
        renderMenu(chatId, session, builder.toString(), keyboardModerationHome(profileService.isAdmin(account.getUserId())));
    }

    private void openAdminOverview(long chatId, UserSession session, UserAccount account, String notice) {
        if (!profileService.isAdmin(account.getUserId())) {
            openModerationHome(chatId, session, account, "Only admins can open the admin overview.");
            return;
        }
        session.resetProfileScreen();
        String text = uiFactory.adminOverviewText(
                profileService.getTotalUsersCount(),
                profileService.getTotalProfilesCount(),
                profileService.getActiveSubscriptionsCount(LocalDate.now()),
                profileService.getSubscriptionPricing()
        );
        renderMenu(chatId, session, notice == null ? text : notice + "\n\n" + text, keyboardAdminOverview());
    }

    private void openAdminPricing(long chatId, UserSession session, UserAccount account, String notice) {
        if (!profileService.isAdmin(account.getUserId())) {
            openModerationHome(chatId, session, account, "Only admins can change subscription pricing.");
            return;
        }
        session.resetProfileScreen();
        if (session.getPendingSubscriptionPriceDays() == null) {
            session.setExpectedInput(ExpectedInput.NONE);
        }
        renderMenu(chatId, session, uiFactory.adminPricingText(profileService.getSubscriptionPricing(), notice), keyboardAdminPricing());
    }

    private void openAdminSubscriptions(long chatId, UserSession session, UserAccount account, int page) {
        if (!profileService.isAdmin(account.getUserId())) {
            renderMenu(chatId, session, "<b>Access denied</b>\nOnly admins can view active subscriptions.", keyboardModerationBack());
            return;
        }
        session.resetProfileScreen();
        List<SubscriptionView> subscriptions = profileService.getActiveSubscriptions(LocalDate.now());
        if (subscriptions.isEmpty()) {
            renderMenu(chatId, session, "<b>Active subscriptions</b>\nNo active subscriptions right now.", keyboardAdminSubscriptionsEmpty());
            return;
        }
        int totalPages = Math.max(1, (subscriptions.size() + SUBSCRIPTIONS_PER_PAGE - 1) / SUBSCRIPTIONS_PER_PAGE);
        int normalizedPage = Math.max(0, Math.min(page, totalPages - 1));
        session.setSubscriptionsPage(normalizedPage);
        int fromIndex = normalizedPage * SUBSCRIPTIONS_PER_PAGE;
        int toIndex = Math.min(fromIndex + SUBSCRIPTIONS_PER_PAGE, subscriptions.size());
        renderMenu(
                chatId,
                session,
                uiFactory.activeSubscriptionsText(subscriptions.subList(fromIndex, toIndex), normalizedPage, totalPages),
                keyboardAdminSubscriptions(normalizedPage, totalPages)
        );
    }

    private void openModerationReport(long chatId, UserSession session, int index) {
        cleanupCardMessages(chatId, session);
        session.resetProfileScreen();
        List<ProfileService.ModerationReportView> reports = profileService.getOpenReports();
        if (reports.isEmpty()) {
            renderMenu(chatId, session, "<b>No open reports</b>\nEverything is clean right now.", keyboardModerationBack());
            return;
        }
        int normalized = Math.max(0, Math.min(index, reports.size() - 1));
        session.setModerationIndex(normalized);
        ProfileService.ModerationReportView view = reports.get(normalized);
        if (view.target() != null) {
            renderProfileScreen(
                    chatId,
                    session,
                    view.target(),
                    uiFactory.moderationReportText(view.report(), view.reporter(), view.target()),
                    keyboardReportModeration(view.report().getId(), normalized, reports.size()),
                    ProfileScreenContext.MODERATION_REPORT,
                    false
            );
            return;
        }
        renderMenu(chatId, session, uiFactory.moderationReportText(view.report(), view.reporter(), view.target()), keyboardReportModeration(view.report().getId(), normalized, reports.size()));
    }

    private void openModeratorManagement(long chatId, UserSession session, UserAccount account, String notice) {
        if (!profileService.isAdmin(account.getUserId())) {
            renderMenu(chatId, session, "<b>Access denied</b>\nOnly admins can manage moderators.", keyboardModerationBack());
            return;
        }
        StringBuilder builder = new StringBuilder();
        if (notice != null) {
            builder.append(notice).append("\n\n");
        }
        builder.append("<b>🧑‍⚖️ Team management</b>\n");
        builder.append("Admins:\n");
        List<UserAccount> admins = profileService.getAdmins();
        if (admins.isEmpty() && config.adminIds().isEmpty()) {
            builder.append("• No additional admins\n");
        } else {
            for (Long adminId : config.adminIds()) {
                builder.append("• ").append(adminId).append(" — env admin\n");
            }
            for (UserAccount admin : admins) {
                builder.append("• ").append(admin.getUserId());
                if (admin.getFirstName() != null) {
                    builder.append(" — ").append(Htmls.escape(admin.getFirstName()));
                }
                builder.append(" (saved admin)\n");
            }
        }
        builder.append("\nModerators:\n");
        List<UserAccount> moderators = profileService.getModerators();
        if (moderators.isEmpty()) {
            builder.append("• No moderators assigned yet.");
        } else {
            for (UserAccount moderator : moderators) {
                builder.append("• ").append(moderator.getUserId());
                if (moderator.getFirstName() != null) {
                    builder.append(" — ").append(Htmls.escape(moderator.getFirstName()));
                }
                builder.append("\n");
            }
        }
        renderMenu(chatId, session, builder.toString(), keyboardModeratorManagement());
    }

    private void sendMatchNotifications(long firstUserId, long secondUserId) {
        UserProfile first = profileService.getProfile(firstUserId);
        UserProfile second = profileService.getProfile(secondUserId);
        if (first == null || second == null) {
            return;
        }
        sendMatchMessage(firstUserId, second);
        sendMatchMessage(secondUserId, first);
    }

    private void sendMatchMessage(long recipientUserId, UserProfile counterpart) {
        StringBuilder builder = new StringBuilder();
        builder.append("<b>It’s a match!</b>\n");
        builder.append("You and ").append(Htmls.escape(counterpart.getName())).append(" liked each other.\n");
        builder.append("Tap the profile mention below to open a chat.\n");
        builder.append("<a href=\"tg://user?id=").append(counterpart.getUserId()).append("\">Open ").append(Htmls.escape(counterpart.getName())).append("</a>");
        InlineKeyboardMarkup keyboard = counterpart.getUsername() == null || counterpart.getUsername().isBlank()
                ? keyboardHomeOnly()
                : uiFactory.keyboard(List.of(
                List.of(ButtonSpec.url("Open @" + counterpart.getUsername(), "https://t.me/" + counterpart.getUsername())),
                List.of(ButtonSpec.callback("Home", "home"))
        ));
        SendMessage message = new SendMessage();
        message.setChatId(recipientUserId);
        message.setParseMode(ParseMode.HTML);
        message.setText(builder.toString());
        message.setReplyMarkup(keyboard);
        try {
            execute(message);
        } catch (TelegramApiException ignored) {
        }
    }

    private void renderMenu(long chatId, UserSession session, String text, InlineKeyboardMarkup keyboard) {
        renderTextScreen(chatId, session, text, keyboard);
    }

    private void renderTextScreen(long chatId, UserSession session, String text, InlineKeyboardMarkup keyboard) {
        clearSubscriptionInvoiceMessage(chatId, session);
        Integer previousMessageId = session.getMenuMessageId();
        Integer targetMessageId = previousMessageId;

        if (targetMessageId != null && session.getScreenKind() != UserSession.ScreenKind.TEXT) {
            deleteMessageSilently(chatId, targetMessageId);
            targetMessageId = null;
            session.setMenuMessageId(null);
        }

        if (targetMessageId != null) {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId);
            edit.setMessageId(targetMessageId);
            edit.setText(text);
            edit.setParseMode(ParseMode.HTML);
            edit.setReplyMarkup(keyboard);
            try {
                execute(edit);
                session.setMenuMessageId(targetMessageId);
                session.setScreenKind(UserSession.ScreenKind.TEXT);
                return;
            } catch (TelegramApiException exception) {
                if (isMessageNotModified(exception)) {
                    session.setMenuMessageId(targetMessageId);
                    session.setScreenKind(UserSession.ScreenKind.TEXT);
                    return;
                }
            }
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode(ParseMode.HTML);
        message.setText(text);
        message.setReplyMarkup(keyboard);
        try {
            Message sent = execute(message);
            session.setMenuMessageId(sent.getMessageId());
            session.setScreenKind(UserSession.ScreenKind.TEXT);
            if (previousMessageId != null && !previousMessageId.equals(sent.getMessageId())) {
                deleteMessageSilently(chatId, previousMessageId);
            }
        } catch (TelegramApiException exception) {
            throw new IllegalStateException("Unable to render menu", exception);
        }
    }

    private void renderProtectedTextScreen(long chatId, UserSession session, String text, InlineKeyboardMarkup keyboard) {
        clearSubscriptionInvoiceMessage(chatId, session);
        Integer previousMessageId = session.getMenuMessageId();
        if (previousMessageId != null) {
            deleteMessageSilently(chatId, previousMessageId);
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode(ParseMode.HTML);
        message.setText(text);
        message.setReplyMarkup(keyboard);
        message.setProtectContent(true);
        try {
            Message sent = execute(message);
            session.setMenuMessageId(sent.getMessageId());
            session.setScreenKind(UserSession.ScreenKind.TEXT);
        } catch (TelegramApiException exception) {
            throw new IllegalStateException("Unable to render protected menu", exception);
        }
    }

    private void renderProtectedPhotoScreen(long chatId,
                                            UserSession session,
                                            String mediaUrl,
                                            String caption,
                                            InlineKeyboardMarkup keyboard) {
        clearSubscriptionInvoiceMessage(chatId, session);
        Integer previousMessageId = session.getMenuMessageId();
        if (previousMessageId != null) {
            deleteMessageSilently(chatId, previousMessageId);
        }

        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setPhoto(new InputFile(mediaUrl));
        sendPhoto.setCaption(caption);
        sendPhoto.setParseMode(ParseMode.HTML);
        sendPhoto.setReplyMarkup(keyboard);
        sendPhoto.setProtectContent(true);
        try {
            Message sent = execute(sendPhoto);
            session.setMenuMessageId(sent.getMessageId());
            session.setScreenKind(UserSession.ScreenKind.PHOTO);
        } catch (TelegramApiException exception) {
            renderProtectedTextScreen(chatId, session, caption, keyboard);
        }
    }

    private void renderProfileScreen(
            long chatId,
            UserSession session,
            UserProfile profile,
            String caption,
            InlineKeyboardMarkup keyboard,
            ProfileScreenContext context,
            boolean preserveMediaIndex
    ) {
        clearSubscriptionInvoiceMessage(chatId, session);
        List<MediaAttachment> media = profile.getMedia();
        if (!preserveMediaIndex
                || session.getProfileScreenContext() != context
                || !Objects.equals(session.getCurrentScreenProfileUserId(), profile.getUserId())) {
            session.setCurrentMediaIndex(0);
        }
        session.setProfileScreenContext(context);
        session.setCurrentScreenProfileUserId(profile.getUserId());

        if (media == null || media.isEmpty()) {
            session.setScreenKind(UserSession.ScreenKind.TEXT);
            renderTextScreen(chatId, session, caption, keyboard);
            return;
        }

        int mediaIndex = Math.max(0, Math.min(session.getCurrentMediaIndex(), media.size() - 1));
        session.setCurrentMediaIndex(mediaIndex);
        MediaAttachment primaryMedia = media.get(mediaIndex);
        String fullCaption = media.size() > 1
                ? caption + "\n\nMedia: <b>" + (mediaIndex + 1) + "/" + media.size() + "</b>"
                : caption;
        InlineKeyboardMarkup decoratedKeyboard = withMediaNavigation(keyboard, mediaIndex, media.size());
        Integer previousMessageId = session.getMenuMessageId();
        Integer targetMessageId = previousMessageId;

        if (targetMessageId != null && session.getScreenKind() == UserSession.ScreenKind.TEXT) {
            deleteMessageSilently(chatId, targetMessageId);
            targetMessageId = null;
            session.setMenuMessageId(null);
        }

        if (targetMessageId != null) {
            EditMessageMedia edit = new EditMessageMedia();
            edit.setChatId(chatId);
            edit.setMessageId(targetMessageId);
            edit.setReplyMarkup(decoratedKeyboard);
            edit.setMedia(buildInputMedia(primaryMedia, trimCaption(fullCaption)));
            try {
                execute(edit);
                session.setMenuMessageId(targetMessageId);
                session.setScreenKind(primaryMedia.getType() == MediaType.PHOTO
                        ? UserSession.ScreenKind.PHOTO
                        : UserSession.ScreenKind.VIDEO);
                return;
            } catch (TelegramApiException exception) {
                if (isMessageNotModified(exception)) {
                    session.setMenuMessageId(targetMessageId);
                    session.setScreenKind(primaryMedia.getType() == MediaType.PHOTO
                            ? UserSession.ScreenKind.PHOTO
                            : UserSession.ScreenKind.VIDEO);
                    return;
                }
            }
        }

        try {
            Message sent;
            if (primaryMedia.getType() == MediaType.PHOTO) {
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId);
                sendPhoto.setPhoto(new InputFile(primaryMedia.getFileId()));
                sendPhoto.setCaption(trimCaption(fullCaption));
                sendPhoto.setParseMode(ParseMode.HTML);
                sendPhoto.setReplyMarkup(decoratedKeyboard);
                sent = execute(sendPhoto);
            } else {
                SendVideo sendVideo = new SendVideo();
                sendVideo.setChatId(chatId);
                sendVideo.setVideo(new InputFile(primaryMedia.getFileId()));
                sendVideo.setCaption(trimCaption(fullCaption));
                sendVideo.setParseMode(ParseMode.HTML);
                sendVideo.setReplyMarkup(decoratedKeyboard);
                sent = execute(sendVideo);
            }
            session.setMenuMessageId(sent.getMessageId());
            session.setScreenKind(primaryMedia.getType() == MediaType.PHOTO
                    ? UserSession.ScreenKind.PHOTO
                    : UserSession.ScreenKind.VIDEO);
            if (previousMessageId != null && !previousMessageId.equals(sent.getMessageId())) {
                deleteMessageSilently(chatId, previousMessageId);
            }
        } catch (TelegramApiException exception) {
            renderTextScreen(chatId, session, caption, decoratedKeyboard);
        }
    }

    private InlineKeyboardMarkup withMediaNavigation(InlineKeyboardMarkup keyboard, int mediaIndex, int mediaCount) {
        if (mediaCount <= 1) {
            return keyboard;
        }

        List<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new ArrayList<>();
        List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> navRow = new ArrayList<>();
        navRow.add(inlineCallbackButton("◀", "media:prev"));
        navRow.add(inlineCallbackButton((mediaIndex + 1) + "/" + mediaCount, "noop"));
        navRow.add(inlineCallbackButton("▶", "media:next"));
        rows.add(navRow);

        if (keyboard != null && keyboard.getKeyboard() != null) {
            rows.addAll(keyboard.getKeyboard());
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton inlineCallbackButton(String text, String data) {
        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton button =
                new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(data);
        return button;
    }

    private InputMedia buildInputMedia(MediaAttachment attachment, String caption) {
        if (attachment.getType() == MediaType.PHOTO) {
            InputMediaPhoto photo = new InputMediaPhoto();
            photo.setMedia(attachment.getFileId());
            photo.setCaption(caption);
            photo.setParseMode(ParseMode.HTML);
            return photo;
        }
        InputMediaVideo video = new InputMediaVideo();
        video.setMedia(attachment.getFileId());
        video.setCaption(caption);
        video.setParseMode(ParseMode.HTML);
        return video;
    }

    private String trimCaption(String value) {
        if (value == null) {
            return "";
        }
        final int maxCaptionLength = 1024;
        return value.length() <= maxCaptionLength ? value : value.substring(0, maxCaptionLength - 1) + "…";
    }

    private void cleanupCardMessages(long chatId, UserSession session) {
        for (Integer messageId : new ArrayList<>(session.getActiveCardMessageIds())) {
            deleteMessageSilently(chatId, messageId);
        }
        session.getActiveCardMessageIds().clear();
    }

    private void clearSubscriptionInvoiceMessage(long chatId, UserSession session) {
        Integer invoiceMessageId = session.getSubscriptionInvoiceMessageId();
        if (invoiceMessageId == null) {
            return;
        }
        session.setSubscriptionInvoiceMessageId(null);
        if (!invoiceMessageId.equals(session.getMenuMessageId())) {
            deleteMessageSilently(chatId, invoiceMessageId);
        }
    }

    private void deleteIncomingMessage(Message message) {
        if (message.getMessageId() != null) {
            deleteMessageSilently(message.getChatId(), message.getMessageId());
        }
    }

    private void deleteMessageSilently(long chatId, int messageId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId);
        deleteMessage.setMessageId(messageId);
        try {
            execute(deleteMessage);
        } catch (TelegramApiException ignored) {
        }
    }

    private void answerCallback(CallbackQuery callbackQuery, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        if (text != null && !text.isBlank()) {
            answer.setText(text);
        }
        try {
            execute(answer);
        } catch (TelegramApiException ignored) {
        }
    }

    private boolean isMessageNotModified(TelegramApiException exception) {
        if (exception == null || exception.getMessage() == null) {
            return false;
        }
        return exception.getMessage().toLowerCase().contains("message is not modified");
    }

    private boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.startsWith("https://") || normalized.startsWith("http://");
    }

    private String normalizeAdButtonLabel(String label, String fallback) {
        if (label == null || label.isBlank()) {
            return fallback;
        }
        String trimmed = label.trim();
        return trimmed.length() <= 32 ? trimmed : trimmed.substring(0, 29) + "...";
    }

    private String extractVideoFileId(Message message) {
        if (message.hasVideo() && message.getVideo() != null) {
            return message.getVideo().getFileId();
        }
        if (message.getAnimation() != null) {
            return message.getAnimation().getFileId();
        }
        if (message.hasDocument()
                && message.getDocument() != null
                && message.getDocument().getMimeType() != null
                && message.getDocument().getMimeType().toLowerCase().startsWith("video/")) {
            return message.getDocument().getFileId();
        }
        return null;
    }

    private PhotoSize largestPhoto(List<PhotoSize> sizes) {
        return sizes.stream().max((left, right) -> Integer.compare(left.getFileSize(), right.getFileSize())).orElse(sizes.getLast());
    }

    private UserSession userSession(User user) {
        return sessionService.get(user.getId());
    }

    private void showFallbackError(long chatId, UserSession session) {
        renderMenu(chatId, session, "<b>Something went wrong</b>\nTry pressing Home and repeating the action.", keyboardHomeOnly());
    }

    private InlineKeyboardMarkup keyboardHome(boolean hasProfile, boolean moderationEnabled) {
        List<List<ButtonSpec>> rows = new ArrayList<>();
        if (hasProfile) {
            rows.add(List.of(ButtonSpec.callback("👤 My profile", "menu:profile"), ButtonSpec.callback("🔎 Browse", "menu:search")));
            rows.add(List.of(ButtonSpec.callback("💌 Who likes me", "menu:likes"), ButtonSpec.callback("💎 Disable ads", "menu:ads")));
        } else {
            rows.add(List.of(ButtonSpec.callback("✨ Create profile", "menu:create")));
            rows.add(List.of(ButtonSpec.callback("💎 Disable ads", "menu:ads")));
        }
        if (moderationEnabled) {
            rows.add(List.of(ButtonSpec.callback("🛡 Moderation", "menu:moderation")));
        }
        return uiFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup keyboardNoProfile() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("✨ Create profile", "menu:create")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardHomeOnly() {
        return uiFactory.keyboard(List.of(List.of(ButtonSpec.callback("🏠 Home", "home"))));
    }

    private InlineKeyboardMarkup keyboardMyProfile() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("✏️ Rebuild profile", "profile:rebuild"), ButtonSpec.callback("🖼 Manage media", "profile:media")),
                List.of(ButtonSpec.callback("🗑 Delete profile", "profile:delete"), ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardDeleteProfileConfirm() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("🗑 Delete now", "profile:delete:confirm")),
                List.of(ButtonSpec.callback("↩️ Cancel", "menu:profile"))
        ));
    }

    private InlineKeyboardMarkup keyboardGender(ProfileDraft draft) {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback(mark(draft.getGender() == Gender.MALE, "Male"), "wizard:gender:MALE"),
                        ButtonSpec.callback(mark(draft.getGender() == Gender.FEMALE, "Female"), "wizard:gender:FEMALE")),
                List.of(ButtonSpec.callback(mark(draft.getGender() == Gender.OTHER, "Other"), "wizard:gender:OTHER")),
                List.of(ButtonSpec.callback("↩️ Cancel", "wizard:cancel"))
        ));
    }

    private InlineKeyboardMarkup keyboardLookingFor(ProfileDraft draft) {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback(mark(draft.getLookingFor() == PartnerPreference.MEN, "Men"), "wizard:looking:MEN"),
                        ButtonSpec.callback(mark(draft.getLookingFor() == PartnerPreference.WOMEN, "Women"), "wizard:looking:WOMEN")),
                List.of(ButtonSpec.callback(mark(draft.getLookingFor() == PartnerPreference.EVERYONE, "Everyone"), "wizard:looking:EVERYONE")),
                List.of(ButtonSpec.callback("⬅️ Back", "wizard:back"), ButtonSpec.callback("↩️ Cancel", "wizard:cancel"))
        ));
    }

    private InlineKeyboardMarkup keyboardGoal(ProfileDraft draft) {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback(mark(draft.getGoal() == Goal.DATING, "Dating"), "wizard:goal:DATING"),
                        ButtonSpec.callback(mark(draft.getGoal() == Goal.FRIENDSHIP, "Friendship"), "wizard:goal:FRIENDSHIP")),
                List.of(ButtonSpec.callback(mark(draft.getGoal() == Goal.LANGUAGE_EXCHANGE, "Language exchange"), "wizard:goal:LANGUAGE_EXCHANGE")),
                List.of(ButtonSpec.callback(mark(draft.getGoal() == Goal.NETWORKING, "Networking"), "wizard:goal:NETWORKING")),
                List.of(ButtonSpec.callback("⬅️ Back", "wizard:back"), ButtonSpec.callback("↩️ Cancel", "wizard:cancel"))
        ));
    }

    private InlineKeyboardMarkup keyboardPrivacy(ProfileDraft draft) {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback(mark(draft.getPrivacyMode() == PrivacyMode.OPEN, "Open"), "wizard:privacy:OPEN"),
                        ButtonSpec.callback(mark(draft.getPrivacyMode() == PrivacyMode.PRIVATE, "Private"), "wizard:privacy:PRIVATE")),
                List.of(ButtonSpec.callback("⬅️ Back", "wizard:back"), ButtonSpec.callback("↩️ Cancel", "wizard:cancel"))
        ));
    }

    private InlineKeyboardMarkup keyboardWizardBack() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("⬅️ Back", "wizard:back"), ButtonSpec.callback("↩️ Cancel", "wizard:cancel"))
        ));
    }

    private InlineKeyboardMarkup keyboardMedia(ProfileDraft draft) {
        List<List<ButtonSpec>> rows = new ArrayList<>();
        List<ButtonSpec> addRow = new ArrayList<>();
        if (draft.canAddPhoto()) {
            addRow.add(ButtonSpec.callback("📷 Add photo", "wizard:mediaPhoto"));
        }
        if (draft.canAddVideo()) {
            addRow.add(ButtonSpec.callback("🎥 Add video", "wizard:mediaVideo"));
        }
        if (!addRow.isEmpty()) {
            rows.add(addRow);
        }
        if (!draft.getMedia().isEmpty()) {
            rows.add(List.of(ButtonSpec.callback("↩️ Remove last", "wizard:mediaRemove"), ButtonSpec.callback("🧹 Clear all", "wizard:mediaClear")));
            rows.add(List.of(ButtonSpec.callback("✅ Save profile", "wizard:save")));
        }
        rows.add(List.of(ButtonSpec.callback("⬅️ Back", "wizard:back"), ButtonSpec.callback("↩️ Cancel", "wizard:cancel")));
        return uiFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup keyboardBrowse() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("❤️ Like", "browse:like"), ButtonSpec.callback("⏭ Skip", "browse:skip")),
                List.of(ButtonSpec.callback("⏮ Back", "browse:back"), ButtonSpec.callback("🚩 Report", "browse:report")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardAfterBrowseEmpty() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("👤 My profile", "menu:profile"), ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardLikes() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("💘 Like back", "likes:back"), ButtonSpec.callback("⏭ Pass", "likes:pass")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardReportEvidence() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("⏭ Skip evidence", "report:skipEvidence")),
                List.of(ButtonSpec.callback("↩️ Back to profile", "report:cancel"))
        ));
    }

    private InlineKeyboardMarkup keyboardReportConfirm() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("📨 Send report", "report:confirm")),
                List.of(ButtonSpec.callback("↩️ Back to profile", "report:cancel"))
        ));
    }

    private InlineKeyboardMarkup keyboardSubscription() {
        SubscriptionPricing pricing = profileService.getSubscriptionPricing();
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("💎 " + pricing.monthlyStars() + " Stars / 30 days", "sub:buy:30")),
                List.of(ButtonSpec.callback("💎 " + pricing.yearlyStars() + " Stars / 365 days", "sub:buy:365")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardAdInterstitial() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("▶️ Continue", "browse:continueAd")),
                List.of(ButtonSpec.callback("💎 Disable ads", "menu:ads"), ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardAdsgramInterstitial(AdsgramAd ad) {
        List<List<ButtonSpec>> rows = new ArrayList<>();
        if (isHttpUrl(ad.getClickUrl())) {
            rows.add(List.of(ButtonSpec.url(normalizeAdButtonLabel(ad.getButtonName(), "🔗 Open offer"), ad.getClickUrl())));
        }
        if (isHttpUrl(ad.getRewardUrl())) {
            rows.add(List.of(ButtonSpec.url(normalizeAdButtonLabel(ad.getRewardButtonName(), "🎁 Claim reward"), ad.getRewardUrl())));
        }
        rows.add(List.of(ButtonSpec.callback("▶️ Continue", "browse:continueAd")));
        rows.add(List.of(ButtonSpec.callback("💎 Disable ads", "menu:ads"), ButtonSpec.callback("🏠 Home", "home")));
        return uiFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup keyboardAdsgramTest(AdsgramAd ad) {
        List<List<ButtonSpec>> rows = new ArrayList<>();
        if (isHttpUrl(ad.getClickUrl())) {
            rows.add(List.of(ButtonSpec.url(normalizeAdButtonLabel(ad.getButtonName(), "🔗 Open offer"), ad.getClickUrl())));
        }
        if (isHttpUrl(ad.getRewardUrl())) {
            rows.add(List.of(ButtonSpec.url(normalizeAdButtonLabel(ad.getRewardButtonName(), "🎁 Claim reward"), ad.getRewardUrl())));
        }
        rows.add(List.of(ButtonSpec.callback("🏠 Home", "home")));
        return uiFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup keyboardModerationHome(boolean admin) {
        List<List<ButtonSpec>> rows = new ArrayList<>();
        rows.add(List.of(ButtonSpec.callback("📂 Open reports", "mod:reports")));
        if (admin) {
            rows.add(List.of(ButtonSpec.callback("📊 Overview", "mod:stats"), ButtonSpec.callback("💳 Subscriptions", "mod:subscriptions")));
            rows.add(List.of(ButtonSpec.callback("💸 Pricing", "mod:pricing"), ButtonSpec.callback("🧑‍⚖️ Team", "mod:mods")));
        }
        rows.add(List.of(ButtonSpec.callback("🏠 Home", "home")));
        return uiFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup keyboardModerationBack() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("⬅️ Back", "mod:home")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardReportModeration(long reportId, int index, int total) {
        List<List<ButtonSpec>> rows = new ArrayList<>();
        rows.add(List.of(
                ButtonSpec.callback("✅ Approve", "mod:report:approve:" + reportId),
                ButtonSpec.callback("❌ Reject", "mod:report:reject:" + reportId)
        ));
        rows.add(List.of(ButtonSpec.callback("⛔ Ban target", "mod:report:ban:" + reportId)));
        List<ButtonSpec> pager = new ArrayList<>();
        if (index > 0) {
            pager.add(ButtonSpec.callback("← Prev", "mod:report:prev"));
        }
        pager.add(ButtonSpec.callback((index + 1) + "/" + total, "noop"));
        if (index + 1 < total) {
            pager.add(ButtonSpec.callback("Next →", "mod:report:next"));
        }
        rows.add(pager);
        rows.add(List.of(ButtonSpec.callback("⬅️ Back", "mod:home"), ButtonSpec.callback("🏠 Home", "home")));
        return uiFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup keyboardModeratorManagement() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("👑 Add admin", "mod:addAdmin"), ButtonSpec.callback("👑 Remove admin", "mod:removeAdmin")),
                List.of(ButtonSpec.callback("🛡 Add moderator", "mod:add"), ButtonSpec.callback("🛡 Remove moderator", "mod:remove")),
                List.of(ButtonSpec.callback("⬅️ Back", "mod:home"), ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardModeratorInput(boolean add) {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("⬅️ Back", "mod:mods")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardAdminOverview() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("💸 Pricing", "mod:pricing"), ButtonSpec.callback("💳 Subscriptions", "mod:subscriptions")),
                List.of(ButtonSpec.callback("🧑‍⚖️ Team", "mod:mods"), ButtonSpec.callback("⬅️ Back", "mod:home")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardAdminPricing() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("💎 Set 30-day price", "mod:price:set:30")),
                List.of(ButtonSpec.callback("💎 Set 365-day price", "mod:price:set:365")),
                List.of(ButtonSpec.callback("⬅️ Back", "mod:stats"), ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardAdminSubscriptions(int page, int totalPages) {
        List<List<ButtonSpec>> rows = new ArrayList<>();
        List<ButtonSpec> pager = new ArrayList<>();
        if (page > 0) {
            pager.add(ButtonSpec.callback("← Prev", "mod:subs:page:" + (page - 1)));
        }
        pager.add(ButtonSpec.callback((page + 1) + "/" + totalPages, "noop"));
        if (page + 1 < totalPages) {
            pager.add(ButtonSpec.callback("Next →", "mod:subs:page:" + (page + 1)));
        }
        rows.add(pager);
        rows.add(List.of(ButtonSpec.callback("📊 Overview", "mod:stats"), ButtonSpec.callback("⬅️ Back", "mod:home")));
        rows.add(List.of(ButtonSpec.callback("🏠 Home", "home")));
        return uiFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup keyboardAdminSubscriptionsEmpty() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("📊 Overview", "mod:stats"), ButtonSpec.callback("⬅️ Back", "mod:home")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardAdminInput(boolean add) {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("⬅️ Back", "mod:mods")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private String mark(boolean selected, String label) {
        return selected ? "• " + label : label;
    }
}
