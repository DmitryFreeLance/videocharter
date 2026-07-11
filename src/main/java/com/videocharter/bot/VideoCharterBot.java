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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.GetUserProfilePhotos;
import org.telegram.telegrambots.meta.api.methods.invoices.SendInvoice;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.UserProfilePhotos;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class VideoCharterBot extends TelegramLongPollingBot {

    private static final Pattern AGE_RANGE_PATTERN = Pattern.compile("\\s*(\\d{1,3})\\s*-\\s*(\\d{1,3})\\s*");
    private static final int COUNTRIES_PER_PAGE = 10;
    private static final int SUBSCRIPTIONS_PER_PAGE = 6;
    private static final int ADMIN_USERS_PER_PAGE = 8;

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

        if (shouldHandleWizardMediaUpload(message, session)) {
            handleWizardMediaInput(message, session, account);
            deleteIncomingMessage(message);
            return;
        }
        if (session.getDraft() != null && handleWizardReplyMessage(message, session, account)) {
            deleteIncomingMessage(message);
            return;
        }
        if (session.getDraft() != null
                && session.getDraft().getStep() == ProfileDraft.WizardStep.MEDIA
                && session.getExpectedInput() == ExpectedInput.NONE) {
            deleteIncomingMessage(message);
            renderWizard(message.getChatId(), session, "Send a photo or a video directly to the bot.");
            return;
        }

        String text = message.getText();
        if (isDebugBrowseCommand(text) && profileService.isAdmin(user.getId())) {
            session.setExpectedInput(ExpectedInput.NONE);
            session.setDraft(null);
            session.resetReportDraft();
            session.resetProfileScreen();
            cleanupCardMessages(message.getChatId(), session);
            deleteIncomingMessage(message);
            openDebugBrowse(message.getChatId(), session, account);
            return;
        }
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

        if (handleCurrentButton(message, session, account)) {
            return;
        }

        if (session.getExpectedInput() != ExpectedInput.NONE) {
            handleExpectedInput(message, session, account);
            deleteIncomingMessage(message);
            return;
        }

        deleteIncomingMessage(message);
        openHome(message.getChatId(), account, session, "Use the buttons below.");
    }

    private boolean isTestCommand(String text) {
        if (text == null) {
            return false;
        }
        return "/test".equals(text) || text.startsWith("/test@");
    }

    private boolean isDebugBrowseCommand(String text) {
        if (text == null) {
            return false;
        }
        return "/debugbrowse".equals(text) || text.startsWith("/debugbrowse@");
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
            case ABOUT -> handleAboutInput(message, session, account);
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

    private void handleAboutInput(Message message, UserSession session, UserAccount account) {
        if (session.getDraft() == null) {
            openHome(message.getChatId(), account, session, "No active profile setup. Start again.");
            return;
        }
        String value = message.getText();
        if (value == null || value.isBlank() || value.length() > 300) {
            renderWizard(message.getChatId(), session, "Send a short text about yourself between 1 and 300 characters.");
            return;
        }
        session.getDraft().setAbout(value.trim());
        session.getDraft().setStep(ProfileDraft.WizardStep.MEDIA);
        session.setExpectedInput(ExpectedInput.NONE);
        renderWizard(message.getChatId(), session, "🖼 Now add your media.");
    }

    private void handleAgeInput(Message message, UserSession session, UserAccount account) {
        if (session.getDraft() == null) {
            openHome(message.getChatId(), account, session, "No active profile setup. Start again.");
            return;
        }
        try {
            int age = Integer.parseInt(Objects.requireNonNullElse(message.getText(), "").trim());
            if (age < 0 || age > 999) {
                throw new NumberFormatException("Out of range");
            }
            session.getDraft().setAge(age);
            session.getDraft().setStep(ProfileDraft.WizardStep.AGE_RANGE);
            session.setExpectedInput(ExpectedInput.NONE);
            renderWizard(message.getChatId(), session, "Send the preferred age range, for example <b>18-35</b>.");
        } catch (Exception ignored) {
            renderWizard(message.getChatId(), session, "Age must be a number between 0 and 999. Minors are not allowed to use the bot.");
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
        if (min < 0 || max > 999 || min > max) {
            renderWizard(message.getChatId(), session, "Preferred age must be sent as a range like <b>18-35</b>, and the minimum must not be greater than the maximum.");
            return;
        }
        session.getDraft().setPreferredAgeMin(min);
        session.getDraft().setPreferredAgeMax(max);
        session.getDraft().setStep(ProfileDraft.WizardStep.PRIVACY);
        session.setExpectedInput(ExpectedInput.NONE);
        renderWizard(message.getChatId(), session, "Choose privacy mode.\nPrivate — your username will be hidden.\nOpen — your username will be shown in the profile.");
    }

    private void handlePhotoInput(Message message, UserSession session, UserAccount account) {
        handleWizardMediaInput(message, session, account);
    }

    private void handleVideoInput(Message message, UserSession session, UserAccount account) {
        handleWizardMediaInput(message, session, account);
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

    private boolean shouldHandleWizardMediaUpload(Message message, UserSession session) {
        if (session.getDraft() == null || session.getDraft().getStep() != ProfileDraft.WizardStep.MEDIA) {
            return false;
        }
        return message.hasPhoto() || extractVideoFileId(message) != null;
    }

    private void handleWizardMediaInput(Message message, UserSession session, UserAccount account) {
        if (session.getDraft() == null) {
            openHome(message.getChatId(), account, session, "No active media editor. Start again.");
            return;
        }

        if (message.hasPhoto()) {
            if (!session.getDraft().canAddPhoto()) {
                session.setExpectedInput(ExpectedInput.NONE);
                renderWizard(message.getChatId(), session, "Photo limit reached. You can keep up to 3 photos, or 1 video plus 2 photos.");
                return;
            }
            session.getDraft().addPhoto(largestPhoto(message.getPhoto()).getFileId());
            session.setExpectedInput(ExpectedInput.NONE);
            renderWizard(message.getChatId(), session, "Photo added.");
            return;
        }

        String fileId = extractVideoFileId(message);
        if (fileId != null) {
            if (!session.getDraft().canAddVideo()) {
                session.setExpectedInput(ExpectedInput.NONE);
                renderWizard(message.getChatId(), session, "Video limit reached. You can keep at most 1 video.");
                return;
            }
            session.getDraft().addVideo(fileId);
            session.setExpectedInput(ExpectedInput.NONE);
            renderWizard(message.getChatId(), session, "Video added.");
            return;
        }

        renderWizard(message.getChatId(), session, "Send a photo or a video.");
    }

    private boolean handleWizardReplyMessage(Message message, UserSession session, UserAccount account) {
        if (session.getDraft() == null || !message.hasText()) {
            return false;
        }

        String text = Objects.requireNonNullElse(message.getText(), "").trim();
        if (text.isEmpty()) {
            return false;
        }

        ProfileDraft draft = session.getDraft();
        switch (draft.getStep()) {
            case GENDER -> {
                if (isCancelChoice(text)) {
                    cancelWizard(message.getChatId(), session, account);
                    return true;
                }
                if (matchesChoice(text, "Male")) {
                    draft.setGender(Gender.MALE);
                } else if (matchesChoice(text, "Female")) {
                    draft.setGender(Gender.FEMALE);
                } else if (matchesChoice(text, "Other")) {
                    draft.setGender(Gender.OTHER);
                } else {
                    renderWizard(message.getChatId(), session, "Choose your gender using the keyboard.");
                    return true;
                }
                draft.setStep(ProfileDraft.WizardStep.LOOKING_FOR);
                renderWizard(message.getChatId(), session, null);
                return true;
            }
            case LOOKING_FOR -> {
                if (isCancelChoice(text)) {
                    cancelWizard(message.getChatId(), session, account);
                    return true;
                }
                if (isBackChoice(text)) {
                    goWizardBack(message.getChatId(), session);
                    return true;
                }
                if (matchesChoice(text, "Men")) {
                    draft.setLookingFor(PartnerPreference.MEN);
                } else if (matchesChoice(text, "Women")) {
                    draft.setLookingFor(PartnerPreference.WOMEN);
                } else if (matchesChoice(text, "Everyone")) {
                    draft.setLookingFor(PartnerPreference.EVERYONE);
                } else {
                    renderWizard(message.getChatId(), session, "Choose who you want to meet using the keyboard.");
                    return true;
                }
                draft.setStep(ProfileDraft.WizardStep.GOAL);
                renderWizard(message.getChatId(), session, null);
                return true;
            }
            case GOAL -> {
                if (isCancelChoice(text)) {
                    cancelWizard(message.getChatId(), session, account);
                    return true;
                }
                if (isBackChoice(text)) {
                    goWizardBack(message.getChatId(), session);
                    return true;
                }
                if (matchesChoice(text, "Dating")) {
                    draft.setGoal(Goal.DATING);
                } else if (matchesChoice(text, "Friendship")) {
                    draft.setGoal(Goal.FRIENDSHIP);
                } else if (matchesChoice(text, "Communication")) {
                    draft.setGoal(Goal.LANGUAGE_EXCHANGE);
                } else if (matchesChoice(text, "Video Calls")) {
                    draft.setGoal(Goal.NETWORKING);
                } else {
                    renderWizard(message.getChatId(), session, "Choose the goal using the keyboard.");
                    return true;
                }
                draft.setStep(ProfileDraft.WizardStep.NAME);
                renderWizard(message.getChatId(), session, null);
                return true;
            }
            case NAME -> {
                if (isCancelChoice(text)) {
                    cancelWizard(message.getChatId(), session, account);
                    return true;
                }
                if (isBackChoice(text)) {
                    goWizardBack(message.getChatId(), session);
                    return true;
                }
                return false;
            }
            case ABOUT -> {
                if (isCancelChoice(text)) {
                    cancelWizard(message.getChatId(), session, account);
                    return true;
                }
                if (isBackChoice(text)) {
                    goWizardBack(message.getChatId(), session);
                    return true;
                }
                return false;
            }
            case AGE -> {
                if (isCancelChoice(text)) {
                    cancelWizard(message.getChatId(), session, account);
                    return true;
                }
                if (isBackChoice(text)) {
                    goWizardBack(message.getChatId(), session);
                    return true;
                }
                return false;
            }
            case AGE_RANGE -> {
                if (isCancelChoice(text)) {
                    cancelWizard(message.getChatId(), session, account);
                    return true;
                }
                if (isBackChoice(text)) {
                    goWizardBack(message.getChatId(), session);
                    return true;
                }
                return false;
            }
            case PRIVACY -> {
                if (isCancelChoice(text)) {
                    cancelWizard(message.getChatId(), session, account);
                    return true;
                }
                if (isBackChoice(text)) {
                    goWizardBack(message.getChatId(), session);
                    return true;
                }
                if (matchesChoice(text, "Open")) {
                    draft.setPrivacyMode(PrivacyMode.OPEN);
                } else if (matchesChoice(text, "Private")) {
                    draft.setPrivacyMode(PrivacyMode.PRIVATE);
                } else {
                    renderWizard(message.getChatId(), session, "Choose privacy mode.\nPrivate — your username will be hidden.\nOpen — your username will be shown in the profile.");
                    return true;
                }
                draft.setStep(ProfileDraft.WizardStep.COUNTRY);
                session.setCountryPage(0);
                renderWizard(message.getChatId(), session, null);
                return true;
            }
            case COUNTRY -> {
                if (isCancelChoice(text)) {
                    cancelWizard(message.getChatId(), session, account);
                    return true;
                }
                if (isBackChoice(text)) {
                    goWizardBack(message.getChatId(), session);
                    return true;
                }
                if ("← Prev".equals(text)) {
                    session.setCountryPage(Math.max(0, session.getCountryPage() - 1));
                    renderWizard(message.getChatId(), session, null);
                    return true;
                }
                if ("Next →".equals(text)) {
                    session.setCountryPage(session.getCountryPage() + 1);
                    renderWizard(message.getChatId(), session, null);
                    return true;
                }
                Optional<Country> country = findCountryFromReply(text);
                if (country.isEmpty()) {
                    renderWizard(message.getChatId(), session, "Choose a country using the keyboard.");
                    return true;
                }
                draft.setCountryCode(country.get().code());
                draft.setCountryName(country.get().name());
                draft.setCountryFlag(country.get().flag());
                draft.setStep(ProfileDraft.WizardStep.ABOUT);
                renderWizard(message.getChatId(), session, null);
                return true;
            }
            case MEDIA -> {
                if (isCancelChoice(text)) {
                    cancelWizard(message.getChatId(), session, account);
                    return true;
                }
                if (isBackChoice(text)) {
                    goWizardBack(message.getChatId(), session);
                    return true;
                }
                if (matchesChoice(text, "Remove last")) {
                    draft.removeLastMedia();
                    renderWizard(message.getChatId(), session, "Removed the last media item.");
                    return true;
                }
                if (matchesChoice(text, "Clear all")) {
                    draft.clearMedia();
                    renderWizard(message.getChatId(), session, "Media cleared.");
                    return true;
                }
                if (matchesChoice(text, "Use Telegram photo")) {
                    addTelegramProfilePhoto(message.getChatId(), session, account);
                    return true;
                }
                if (matchesChoice(text, "Preview profile")) {
                    if (draft.getMedia().isEmpty()) {
                        renderWizard(message.getChatId(), session, "Add at least one media item before preview.");
                        return true;
                    }
                    draft.setStep(ProfileDraft.WizardStep.PREVIEW);
                    renderWizard(message.getChatId(), session, null);
                    return true;
                }
                renderWizard(message.getChatId(), session, "Send a photo or a video directly to the bot, or use the Telegram photo button.");
                return true;
            }
            case PREVIEW -> {
                if (matchesChoice(text, "Publish profile")) {
                    profileService.saveProfile(account.getUserId(), account.getUsername(), draft);
                    session.setDraft(null);
                    session.setExpectedInput(ExpectedInput.NONE);
                    clearWizardUi(message.getChatId(), session, true);
                    openMyProfile(message.getChatId(), session, account);
                    return true;
                }
                if (matchesChoice(text, "Edit media")) {
                    draft.setStep(ProfileDraft.WizardStep.MEDIA);
                    renderWizard(message.getChatId(), session, "Update anything you want, then preview again.");
                    return true;
                }
                if (isBackChoice(text)) {
                    goWizardBack(message.getChatId(), session);
                    return true;
                }
                if (isCancelChoice(text)) {
                    cancelWizard(message.getChatId(), session, account);
                    return true;
                }
                renderWizard(message.getChatId(), session, "Use the keyboard to publish or edit your profile.");
                return true;
            }
        }
        return false;
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
            session.setAdminUsersPage(0);
            openModeratorManagement(message.getChatId(), session, account, add ? "Admin added." : "Admin removed.");
        } catch (Exception ignored) {
            if (add) {
                openAdminUserPicker(message.getChatId(), session, account, session.getAdminUsersPage(), "Send a numeric Telegram user ID or choose a user below.");
                return;
            }
            renderMenu(
                    message.getChatId(),
                    session,
                    "<b>👑 Remove admin</b>\nSend a numeric Telegram user ID.",
                    keyboardAdminInput(false)
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

    private boolean handleCurrentButton(Message message, UserSession session, UserAccount account) {
        if (!message.hasText()) {
            return false;
        }
        String text = Objects.requireNonNullElse(message.getText(), "").trim();
        if (text.isEmpty()) {
            return false;
        }
        String action = session.getCurrentButtonActions().get(text);
        if (action == null) {
            return false;
        }
        deleteIncomingMessage(message);
        if (limiterService.isTooFast(account.getUserId())) {
            return true;
        }
        dispatchAction(message.getChatId(), session, account, action);
        return true;
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

        String data = callbackQuery.getData();
        if (!"noop".equals(data)) {
            dispatchAction(callbackQuery.getMessage().getChatId(), session, account, data);
        }
        answerCallback(callbackQuery, null);
    }

    private void dispatchAction(long chatId, UserSession session, UserAccount account, String data) {
        if ("home".equals(data)) {
            session.setExpectedInput(ExpectedInput.NONE);
            session.setDraft(null);
            session.resetReportDraft();
            session.resetProfileScreen();
            cleanupCardMessages(chatId, session);
            openHome(chatId, account, session, null);
            return;
        }
        if ("menu:create".equals(data)) {
            startWizard(chatId, session, null);
            return;
        }
        if ("menu:profile".equals(data)) {
            openMyProfile(chatId, session, account);
            return;
        }
        if ("menu:search".equals(data)) {
            openSearch(chatId, session, account, true);
            return;
        }
        if ("menu:likes".equals(data)) {
            openLikes(chatId, session, account);
            return;
        }
        if ("menu:ads".equals(data)) {
            openSubscription(chatId, session, account, null);
            return;
        }
        if ("menu:moderation".equals(data)) {
            openModerationHome(chatId, session, account, null);
            return;
        }
        if ("profile:rebuild".equals(data)) {
            startWizard(chatId, session, profileService.getProfile(account.getUserId()));
            return;
        }
        if ("profile:media".equals(data)) {
            UserProfile current = profileService.getProfile(account.getUserId());
            if (current == null) {
                openHome(chatId, account, session, "Create a profile first.");
                return;
            }
            ProfileDraft draft = ProfileDraft.fromProfile(current);
            draft.setStep(ProfileDraft.WizardStep.MEDIA);
            session.setDraft(draft);
            session.setExpectedInput(ExpectedInput.NONE);
            renderWizard(chatId, session, "Manage your media.");
            return;
        }
        if ("profile:delete".equals(data)) {
            renderMenu(chatId, session, "<b>Delete profile</b>\nThis will remove your profile, likes and matches.", keyboardDeleteProfileConfirm());
            return;
        }
        if ("profile:delete:confirm".equals(data)) {
            cleanupCardMessages(chatId, session);
            profileService.deleteProfile(account.getUserId());
            session.setDraft(null);
            openHome(chatId, account, session, "Your profile was deleted.");
            return;
        }
        if (data.startsWith("wizard:")) {
            handleWizardCallback(chatId, session, account, data);
            return;
        }
        if (data.startsWith("media:")) {
            handleProfileMediaCallback(chatId, session, account, data);
            return;
        }
        if (data.startsWith("browse:")) {
            handleBrowseAction(chatId, session, account, data);
            return;
        }
        if (data.startsWith("likes:")) {
            handleLikesAction(chatId, session, account, data);
            return;
        }
        if (data.startsWith("report:")) {
            handleReportCallback(chatId, session, account, data);
            return;
        }
        if (data.startsWith("sub:buy:")) {
            int days = Integer.parseInt(data.substring("sub:buy:".length()));
            sendSubscriptionInvoice(chatId, session, days);
            return;
        }
        if (data.startsWith("mod:")) {
            handleModerationCallback(chatId, session, account, data);
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
            session.getDraft().setStep(ProfileDraft.WizardStep.ABOUT);
            renderWizard(chatId, session, "💬 Send a short text about yourself.");
            return;
        }
        if ("mediaPhoto".equals(parts[1])) {
            session.setExpectedInput(ExpectedInput.NONE);
            session.getDraft().setStep(ProfileDraft.WizardStep.MEDIA);
            renderWizard(chatId, session, "Send a photo or video in a single message.");
            return;
        }
        if ("mediaVideo".equals(parts[1])) {
            session.setExpectedInput(ExpectedInput.NONE);
            session.getDraft().setStep(ProfileDraft.WizardStep.MEDIA);
            renderWizard(chatId, session, "Send a photo or video in a single message.");
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
            session.setExpectedInput(ExpectedInput.NONE);
            session.getDraft().setStep(ProfileDraft.WizardStep.PREVIEW);
            renderDraftPreview(chatId, session, account, "This is how other users will see your profile.");
            return;
        }
        if ("publish".equals(parts[1])) {
            profileService.saveProfile(account.getUserId(), account.getUsername(), session.getDraft());
            session.setDraft(null);
            session.setExpectedInput(ExpectedInput.NONE);
            openMyProfile(chatId, session, account);
            return;
        }
        if ("editPreview".equals(parts[1])) {
            session.setExpectedInput(ExpectedInput.NONE);
            session.getDraft().setStep(ProfileDraft.WizardStep.MEDIA);
            renderWizard(chatId, session, "Update anything you want, then preview again.");
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
            case ABOUT -> session.getDraft().setStep(ProfileDraft.WizardStep.COUNTRY);
            case AGE -> session.getDraft().setStep(ProfileDraft.WizardStep.NAME);
            case AGE_RANGE -> session.getDraft().setStep(ProfileDraft.WizardStep.AGE);
            case PRIVACY -> session.getDraft().setStep(ProfileDraft.WizardStep.AGE_RANGE);
            case COUNTRY -> session.getDraft().setStep(ProfileDraft.WizardStep.PRIVACY);
            case MEDIA -> session.getDraft().setStep(ProfileDraft.WizardStep.ABOUT);
            case PREVIEW -> session.getDraft().setStep(ProfileDraft.WizardStep.MEDIA);
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
            case DRAFT_PREVIEW -> {
                if (session.getDraft() == null) {
                    openHome(chatId, account, session, "The draft preview is no longer available.");
                    return;
                }
                renderDraftPreview(chatId, session, account, "This is how other users will see your profile.");
            }
            case NONE -> openHome(chatId, account, session, null);
        }
    }

    private void handleBrowseAction(long chatId, UserSession session, UserAccount account, String data) {
        Long currentProfileId = session.getCurrentBrowseProfileId();
        switch (data) {
            case "browse:like" -> {
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
                showNextBrowseCandidate(chatId, session, account, true);
            }
            case "browse:back" -> {
                if (session.getBrowseHistory().isEmpty()) {
                    return;
                }
                Long previousId = session.getBrowseHistory().pop();
                UserProfile previous = profileService.getProfile(previousId);
                if (previous == null) {
                    showNextBrowseCandidate(chatId, session, account, false);
                    return;
                }
                showBrowseProfile(chatId, session, previous, false);
            }
            case "browse:report" -> {
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
            default -> {
            }
        }
    }

    private void handleLikesAction(long chatId, UserSession session, UserAccount account, String data) {
        Long currentProfileId = session.getCurrentBrowseProfileId();
        if (currentProfileId == null) {
            openLikes(chatId, session, account);
            return;
        }
        switch (data) {
            case "likes:back" -> {
                profileService.likeProfile(account.getUserId(), currentProfileId);
                sendMatchNotifications(account.getUserId(), currentProfileId);
                openLikes(chatId, session, account);
            }
            case "likes:pass" -> {
                profileService.dismissIncomingLike(account.getUserId(), currentProfileId);
                openLikes(chatId, session, account);
            }
            default -> {
            }
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
            session.setAdminUsersPage(0);
            openAdminUserPicker(chatId, session, account, 0, null);
            return;
        }
        if ("mod:removeAdmin".equals(data)) {
            session.setExpectedInput(ExpectedInput.REMOVE_ADMIN_ID);
            renderMenu(chatId, session, "<b>👑 Remove admin</b>\nSend a numeric Telegram user ID.", keyboardAdminInput(false));
            return;
        }
        if (data.startsWith("mod:addAdmin:page:")) {
            session.setExpectedInput(ExpectedInput.ADD_ADMIN_ID);
            int page = Integer.parseInt(data.substring("mod:addAdmin:page:".length()));
            openAdminUserPicker(chatId, session, account, page, null);
            return;
        }
        if (data.startsWith("mod:addAdmin:pick:")) {
            session.setExpectedInput(ExpectedInput.NONE);
            long userId = Long.parseLong(data.substring("mod:addAdmin:pick:".length()));
            profileService.setAdmin(userId, true);
            openAdminUserPicker(chatId, session, account, session.getAdminUsersPage(), "Admin added: <code>" + userId + "</code>");
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
        clearWizardUi(chatId, session, true);
        session.setDraft(currentProfile == null ? ProfileDraft.empty() : ProfileDraft.fromProfile(currentProfile));
        session.getDraft().setStep(ProfileDraft.WizardStep.GENDER);
        renderWizard(chatId, session, "Let’s build your profile.");
    }

    private void renderWizard(long chatId, UserSession session, String extra) {
        ProfileDraft draft = session.getDraft();
        if (draft == null) {
            clearWizardUi(chatId, session, true);
            renderMenu(chatId, session, "<b>Profile setup</b>\nNo active draft.", keyboardHomeOnly());
            return;
        }
        draft.setStep(Objects.requireNonNullElse(draft.getStep(), ProfileDraft.WizardStep.GENDER));
        deleteMenuMessageSilently(chatId, session);

        switch (draft.getStep()) {
            case GENDER -> {
                session.setExpectedInput(ExpectedInput.NONE);
                renderWizardPrompt(chatId, session,
                        extra == null ? "🧍 Choose your gender." : extra,
                        replyKeyboard(List.of(
                                List.of("👨 Male", "👩 Female"),
                                List.of("✨ Other"),
                                List.of("↩️ Cancel")
                        )));
            }
            case LOOKING_FOR -> {
                session.setExpectedInput(ExpectedInput.NONE);
                renderWizardPrompt(chatId, session,
                        extra == null ? "💞 Who are you looking for?" : extra,
                        replyKeyboard(List.of(
                                List.of("👨 Men", "👩 Women"),
                                List.of("🌍 Everyone"),
                                List.of("⬅️ Back", "↩️ Cancel")
                        )));
            }
            case GOAL -> {
                session.setExpectedInput(ExpectedInput.NONE);
                renderWizardPrompt(chatId, session,
                        extra == null ? "🎯 Choose your goal." : extra,
                        replyKeyboard(List.of(
                                List.of("💘 Dating", "🤝 Friendship"),
                                List.of("💬 Communication", "📹 Video Calls"),
                                List.of("⬅️ Back", "↩️ Cancel")
                        )));
            }
            case NAME -> {
                session.setExpectedInput(ExpectedInput.NAME);
                renderWizardPrompt(chatId, session,
                        extra == null ? "🪪 Send your name." : extra,
                        replyKeyboard(List.of(List.of("⬅️ Back", "↩️ Cancel"))));
            }
            case ABOUT -> {
                session.setExpectedInput(ExpectedInput.ABOUT);
                renderWizardPrompt(chatId, session,
                        extra == null ? "💬 Send a short text about yourself." : extra,
                        replyKeyboard(List.of(List.of("⬅️ Back", "↩️ Cancel"))));
            }
            case AGE -> {
                session.setExpectedInput(ExpectedInput.AGE);
                renderWizardPrompt(chatId, session,
                        extra == null ? "🎂 Send your age.\nMinors are not allowed to use the bot." : extra,
                        replyKeyboard(List.of(List.of("⬅️ Back", "↩️ Cancel"))));
            }
            case AGE_RANGE -> {
                session.setExpectedInput(ExpectedInput.AGE_RANGE);
                renderWizardPrompt(chatId, session,
                        extra == null ? "📏 Send preferred age, for example 18-35." : extra,
                        replyKeyboard(List.of(List.of("⬅️ Back", "↩️ Cancel"))));
            }
            case PRIVACY -> {
                session.setExpectedInput(ExpectedInput.NONE);
                renderWizardPrompt(chatId, session,
                        extra == null ? "🔒 Choose privacy mode.\nPrivate — your username will be hidden.\nOpen — your username will be shown in the profile." : extra,
                        replyKeyboard(List.of(
                                List.of("🌐 Open", "🙈 Private"),
                                List.of("⬅️ Back", "↩️ Cancel")
                        )));
            }
            case COUNTRY -> renderCountryPage(chatId, session, extra);
            case MEDIA -> {
                session.setExpectedInput(ExpectedInput.NONE);
                String prompt = extra == null
                        ? "🖼 Send photos or one video directly to the bot.\nYou can keep up to 3 photos, or 1 video plus 2 photos.\nYou can also use your Telegram profile photo.\nCurrent media: <b>" + draft.getMedia().size() + "</b>"
                        : extra + "\n\nCurrent media: <b>" + draft.getMedia().size() + "</b>";
                renderWizardPrompt(chatId, session, prompt, mediaReplyKeyboard(draft));
            }
            case PREVIEW -> renderDraftPreview(chatId, session, resolveAccountForChat(chatId), extra == null ? "This is how other users will see your profile." : extra);
        }
    }

    private void renderCountryPage(long chatId, UserSession session, String extra) {
        List<Country> countries = profileService.getSortedCountries();
        int totalPages = Math.max(1, (countries.size() + COUNTRIES_PER_PAGE - 1) / COUNTRIES_PER_PAGE);
        int page = Math.max(0, Math.min(session.getCountryPage(), totalPages - 1));
        session.setCountryPage(page);

        int fromIndex = page * COUNTRIES_PER_PAGE;
        int toIndex = Math.min(fromIndex + COUNTRIES_PER_PAGE, countries.size());
        List<List<String>> rows = new ArrayList<>();
        for (int index = fromIndex; index < toIndex; index += 2) {
            List<String> row = new ArrayList<>();
            Country first = countries.get(index);
            row.add(first.flag() + " " + first.name());
            if (index + 1 < toIndex) {
                Country second = countries.get(index + 1);
                row.add(second.flag() + " " + second.name());
            }
            rows.add(row);
        }
        List<String> pager = new ArrayList<>();
        if (page > 0) {
            pager.add("← Prev");
        }
        if (page < totalPages - 1) {
            pager.add("Next →");
        }
        if (!pager.isEmpty()) {
            rows.add(pager);
        }
        rows.add(List.of("⬅️ Back", "↩️ Cancel"));
        renderWizardPrompt(chatId, session,
                (extra == null ? "🌍 Choose your country." : extra) + "\nPopular countries move up automatically.",
                replyKeyboard(rows));
    }

    private void renderDraftPreview(long chatId, UserSession session, UserAccount account, String extra) {
        if (session.getDraft() == null) {
            openHome(chatId, account, session, "No active draft to preview.");
            return;
        }
        UserProfile preview = session.getDraft().toPreviewProfile(account.getUserId(), account.getUsername());
        StringBuilder caption = new StringBuilder("<b>✅ Profile preview</b>\n");
        if (extra != null && !extra.isBlank()) {
            caption.append(extra).append("\n\n");
        } else {
            caption.append("\n");
        }
        caption.append(uiFactory.browseCard(preview));
        renderProfileScreen(
                chatId,
                session,
                preview,
                caption.toString(),
                null,
                ProfileScreenContext.DRAFT_PREVIEW,
                false
        );
        renderWizardPrompt(chatId, session, "Is everything correct?", replyKeyboard(List.of(
                List.of("✅ Publish profile", "✏️ Edit media"),
                List.of("⬅️ Back", "↩️ Cancel")
        )));
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
                    renderAnchoredTextCard(
                            chatId,
                            session,
                            "✨ 📣",
                            uiFactory.adInterstitialText(decision.freeLimit(), decision.viewedToday()),
                            keyboardAdInterstitial(),
                            false
                    );
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
        builder.append(ad.getTextHtml()).append("\n\n");
        if (isHttpUrl(ad.getClickUrl())) {
            builder.append("<a href=\"").append(Htmls.escape(ad.getClickUrl())).append("\">Open offer</a>\n\n");
        }
        builder.append("Viewed today: <b>").append(decision.viewedToday()).append("</b>\n");
        builder.append("Free limit today: <b>").append(decision.freeLimit()).append("</b>\n");
        builder.append("You can continue to the next profile at any time.");

        InlineKeyboardMarkup keyboard = keyboardAdsgramInterstitial(ad);
        if (isHttpUrl(ad.getImageUrl())) {
            renderProtectedPhotoScreen(chatId, session, ad.getImageUrl(), trimCaption(builder.toString()), keyboard);
            return true;
        }

        renderAnchoredTextCard(chatId, session, "✨ 📣", builder.toString(), keyboard, true);
        return true;
    }

    private void openTestAd(long chatId, UserSession session, UserAccount account) {
        Optional<AdsgramAd> maybeAd = adsgramService.pickBestAd(account.getUserId(), null);
        if (maybeAd.isEmpty()) {
            renderMenu(chatId, session, "<b>📣 Test ad</b>\nNo ad was returned by Adsgram right now. Try again in a moment.", keyboardHomeOnly());
            return;
        }

        AdsgramAd ad = maybeAd.get();
        StringBuilder text = new StringBuilder("<b>📣 Test ad</b>\n\n");
        text.append(ad.getTextHtml());
        if (isHttpUrl(ad.getClickUrl())) {
            text.append("\n\n<a href=\"").append(Htmls.escape(ad.getClickUrl())).append("\">Open offer</a>");
        }
        text.append("\n\nThis preview was requested manually with <code>/test</code>.");
        InlineKeyboardMarkup keyboard = keyboardAdsgramTest(ad);
        if (isHttpUrl(ad.getImageUrl())) {
            renderProtectedPhotoScreen(chatId, session, ad.getImageUrl(), trimCaption(text.toString()), keyboard);
            return;
        }
        renderAnchoredTextCard(chatId, session, "✨ 📣", text.toString(), keyboard, true);
    }

    private void openDebugBrowse(long chatId, UserSession session, UserAccount account) {
        UserProfile me = profileService.getProfile(account.getUserId());
        List<UserProfile> candidates = me == null
                ? List.of()
                : profileService.findBrowseCandidates(account.getUserId(), session.getSeenProfileIds(), 5);
        int freeLimit = profileService.getFreeViewLimit(LocalDate.now());
        int currentViews = account.getLastViewDate() != null && account.getLastViewDate().equals(LocalDate.now())
                ? account.getViewsToday()
                : 0;
        int nextViews = currentViews + 1;
        int postLimitIndex = Math.max(0, nextViews - freeLimit);
        boolean adOnNextProfile = !profileService.hasActiveSubscription(account.getUserId(), LocalDate.now())
                && postLimitIndex > 0
                && postLimitIndex % 2 == 1;

        StringBuilder builder = new StringBuilder();
        builder.append("<b>🧪 Browse debug</b>\n");
        builder.append("Data file: <code>").append(Htmls.escape(config.dataFile().toString())).append("</code>\n");
        builder.append("User ID: <code>").append(account.getUserId()).append("</code>\n");
        builder.append("Profile exists: <b>").append(me != null ? "yes" : "no").append("</b>\n");
        builder.append("Ads disabled by subscription: <b>")
                .append(profileService.hasActiveSubscription(account.getUserId(), LocalDate.now()) ? "yes" : "no")
                .append("</b>\n");
        builder.append("Views today: <b>").append(currentViews).append("</b>\n");
        builder.append("Free daily limit: <b>").append(freeLimit).append("</b>\n");
        builder.append("Ad on next profile: <b>").append(adOnNextProfile ? "yes" : "no").append("</b>\n");
        builder.append("Total profiles: <b>").append(profileService.getTotalProfilesCount()).append("</b>\n");
        builder.append("Seen profiles in session: <b>").append(session.getSeenProfileIds().size()).append("</b>\n");
        builder.append("Candidate sample count: <b>").append(candidates.size()).append("</b>\n");

        if (me != null) {
            builder.append("\n<b>Your profile</b>\n");
            builder.append("Gender: ").append(me.getGender().label()).append("\n");
            builder.append("Looking for: ").append(me.getLookingFor().label()).append("\n");
            builder.append("Goal: ").append(me.getGoal().label()).append("\n");
            builder.append("Age: ").append(me.getAge()).append("\n");
            builder.append("Preferred age: ").append(me.getPreferredAgeMin()).append("-").append(me.getPreferredAgeMax()).append("\n");
            builder.append("Country: ").append(Htmls.escape(me.getCountryFlag())).append(" ").append(Htmls.escape(me.getCountryName())).append("\n");
        }

        if (!candidates.isEmpty()) {
            builder.append("\n<b>Sample candidates</b>\n");
            for (UserProfile candidate : candidates) {
                builder.append("• ")
                        .append(Htmls.escape(candidate.getName()))
                        .append(", ")
                        .append(candidate.getAge())
                        .append(" — ")
                        .append(Htmls.escape(candidate.getCountryCode()))
                        .append(", ")
                        .append(candidate.getGender().label())
                        .append(", ")
                        .append(candidate.getLookingFor().label())
                        .append("\n");
            }
        }

        renderMenu(chatId, session, builder.toString(), keyboardHomeOnly());
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
        builder.append("Use the buttons below to review reports");
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

    private void openAdminUserPicker(long chatId, UserSession session, UserAccount account, int page, String notice) {
        if (!profileService.isAdmin(account.getUserId())) {
            openModerationHome(chatId, session, account, "Only admins can manage admins.");
            return;
        }

        List<UserAccount> users = profileService.getAllUsers();
        if (users.isEmpty()) {
            renderMenu(chatId, session, "<b>👑 Add admin</b>\nNo users are available yet.", keyboardAdminInput(true));
            return;
        }

        int totalPages = Math.max(1, (users.size() + ADMIN_USERS_PER_PAGE - 1) / ADMIN_USERS_PER_PAGE);
        int normalizedPage = Math.max(0, Math.min(page, totalPages - 1));
        session.setAdminUsersPage(normalizedPage);

        int fromIndex = normalizedPage * ADMIN_USERS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ADMIN_USERS_PER_PAGE, users.size());

        StringBuilder builder = new StringBuilder();
        if (notice != null) {
            builder.append(notice).append("\n\n");
        }
        builder.append("<b>👑 Add admin</b>\n");
        builder.append("Send a numeric Telegram user ID or tap a user below.\n");
        builder.append("Page <b>").append(normalizedPage + 1).append("/").append(totalPages).append("</b>\n\n");

        for (UserAccount user : users.subList(fromIndex, toIndex)) {
            builder.append("• <code>").append(user.getUserId()).append("</code>");
            if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
                builder.append(" — ").append(Htmls.escape(user.getFirstName()));
            }
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                builder.append(" (@").append(Htmls.escape(user.getUsername())).append(")");
            }
            if (profileService.isAdmin(user.getUserId())) {
                builder.append(" <b>[admin]</b>");
            }
            builder.append("\n");
        }

        renderMenu(chatId, session, builder.toString(), keyboardAdminPicker(users.subList(fromIndex, toIndex), normalizedPage, totalPages));
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
        SendMessage message = new SendMessage();
        message.setChatId(recipientUserId);
        message.setParseMode(ParseMode.HTML);
        message.setText(builder.toString());
        try {
            execute(message);
        } catch (TelegramApiException ignored) {
        }
    }

    private void renderMenu(long chatId, UserSession session, String text, InlineKeyboardMarkup keyboard) {
        clearWizardUi(chatId, session, true);
        renderTextScreen(chatId, session, text, keyboard);
    }

    private void renderWizardPrompt(long chatId, UserSession session, String text, ReplyKeyboardMarkup keyboard) {
        Integer previousPromptMessageId = session.getWizardPromptMessageId();
        session.getCurrentButtonActions().clear();

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode(ParseMode.HTML);
        message.setText(text);
        message.setReplyMarkup(keyboard);
        try {
            Message sent = execute(message);
            session.setWizardPromptMessageId(sent.getMessageId());
            if (previousPromptMessageId != null && !previousPromptMessageId.equals(sent.getMessageId())) {
                deleteMessageSilently(chatId, previousPromptMessageId);
            }
        } catch (TelegramApiException exception) {
            throw new IllegalStateException("Unable to render wizard prompt", exception);
        }
    }

    private ReplyKeyboardMarkup replyKeyboard(List<List<String>> rows) {
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        for (List<String> rowValues : rows) {
            KeyboardRow row = new KeyboardRow();
            for (String value : rowValues) {
                KeyboardButton button = new KeyboardButton();
                button.setText(value);
                row.add(button);
            }
            keyboardRows.add(row);
        }
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(keyboardRows);
        markup.setResizeKeyboard(true);
        markup.setSelective(false);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    private ReplyKeyboardMarkup mediaReplyKeyboard(ProfileDraft draft) {
        List<List<String>> rows = new ArrayList<>();
        if (draft.canAddPhoto()) {
            rows.add(List.of("📱 Use Telegram photo"));
        }
        if (!draft.getMedia().isEmpty()) {
            rows.add(List.of("↩️ Remove last", "🧹 Clear all"));
        }
        rows.add(List.of("✅ Preview profile"));
        rows.add(List.of("⬅️ Back", "↩️ Cancel"));
        return replyKeyboard(rows);
    }

    private ReplyKeyboardMarkup replyKeyboardFromInline(UserSession session, InlineKeyboardMarkup keyboard) {
        session.getCurrentButtonActions().clear();
        if (keyboard == null || keyboard.getKeyboard() == null || keyboard.getKeyboard().isEmpty()) {
            return null;
        }

        List<List<String>> rows = new ArrayList<>();
        Map<String, String> actions = new LinkedHashMap<>();
        for (List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> inlineRow : keyboard.getKeyboard()) {
            List<String> row = new ArrayList<>();
            for (org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton button : inlineRow) {
                if (button == null || button.getText() == null || button.getText().isBlank()) {
                    continue;
                }
                if (button.getCallbackData() == null || "noop".equals(button.getCallbackData())) {
                    continue;
                }
                row.add(button.getText());
                actions.put(button.getText(), button.getCallbackData());
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }

        session.getCurrentButtonActions().putAll(actions);
        return rows.isEmpty() ? null : replyKeyboard(rows);
    }

    private void clearWizardUi(long chatId, UserSession session, boolean removeKeyboard) {
        Integer wizardPromptMessageId = session.getWizardPromptMessageId();
        session.setWizardPromptMessageId(null);
        if (wizardPromptMessageId != null) {
            deleteMessageSilently(chatId, wizardPromptMessageId);
        }
        if (removeKeyboard && wizardPromptMessageId != null) {
            sendReplyKeyboardRemoval(chatId);
        }
    }

    private void sendReplyKeyboardRemoval(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(" ");
        ReplyKeyboardRemove remove = new ReplyKeyboardRemove();
        remove.setRemoveKeyboard(true);
        message.setReplyMarkup(remove);
        try {
            Message sent = execute(message);
            deleteMessageSilently(chatId, sent.getMessageId());
        } catch (TelegramApiException ignored) {
        }
    }

    private void deleteMenuMessageSilently(long chatId, UserSession session) {
        Integer menuMessageId = session.getMenuMessageId();
        if (menuMessageId != null) {
            deleteMessageSilently(chatId, menuMessageId);
            session.setMenuMessageId(null);
        }
    }

    private void renderTextScreen(long chatId, UserSession session, String text, InlineKeyboardMarkup keyboard) {
        clearSubscriptionInvoiceMessage(chatId, session);
        Integer previousMessageId = session.getMenuMessageId();
        if (previousMessageId != null) {
            deleteMessageSilently(chatId, previousMessageId);
            session.setMenuMessageId(null);
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode(ParseMode.HTML);
        message.setText(text);
        ReplyKeyboardMarkup replyKeyboard = replyKeyboardFromInline(session, keyboard);
        if (replyKeyboard != null) {
            message.setReplyMarkup(replyKeyboard);
        } else {
            ReplyKeyboardRemove remove = new ReplyKeyboardRemove();
            remove.setRemoveKeyboard(true);
            message.setReplyMarkup(remove);
        }
        try {
            Message sent = execute(message);
            session.setMenuMessageId(sent.getMessageId());
            session.setScreenKind(UserSession.ScreenKind.TEXT);
        } catch (TelegramApiException exception) {
            throw new IllegalStateException("Unable to render menu", exception);
        }
    }

    private void renderProtectedTextScreen(long chatId, UserSession session, String text, InlineKeyboardMarkup keyboard) {
        clearSubscriptionInvoiceMessage(chatId, session);
        Integer previousMessageId = session.getMenuMessageId();
        if (previousMessageId != null) {
            deleteMessageSilently(chatId, previousMessageId);
            session.setMenuMessageId(null);
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode(ParseMode.HTML);
        message.setText(text);
        ReplyKeyboardMarkup replyKeyboard = replyKeyboardFromInline(session, keyboard);
        if (replyKeyboard != null) {
            message.setReplyMarkup(replyKeyboard);
        } else {
            ReplyKeyboardRemove remove = new ReplyKeyboardRemove();
            remove.setRemoveKeyboard(true);
            message.setReplyMarkup(remove);
        }
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
            session.setMenuMessageId(null);
        }
        cleanupCardMessages(chatId, session);
        showTransientKeyboard(chatId, session, keyboard, "✨ 📣");

        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setPhoto(new InputFile(mediaUrl));
        sendPhoto.setCaption(caption);
        sendPhoto.setParseMode(ParseMode.HTML);
        sendPhoto.setProtectContent(true);
        try {
            Message sent = execute(sendPhoto);
            session.getActiveCardMessageIds().add(sent.getMessageId());
            session.setScreenKind(UserSession.ScreenKind.TEXT);
        } catch (TelegramApiException exception) {
            renderProtectedTextScreen(chatId, session, caption, keyboard);
        }
    }

    private void renderAnchoredTextCard(long chatId,
                                        UserSession session,
                                        String anchorText,
                                        String text,
                                        InlineKeyboardMarkup keyboard,
                                        boolean protectContent) {
        clearSubscriptionInvoiceMessage(chatId, session);
        cleanupCardMessages(chatId, session);
        if (keyboard != null) {
            showTransientKeyboard(chatId, session, keyboard, anchorText);
        } else {
            Integer previousMessageId = session.getMenuMessageId();
            if (previousMessageId != null) {
                deleteMessageSilently(chatId, previousMessageId);
                session.setMenuMessageId(null);
            }
            session.getCurrentButtonActions().clear();
            session.setScreenKind(UserSession.ScreenKind.TEXT);
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode(ParseMode.HTML);
        message.setText(text);
        if (protectContent) {
            message.setProtectContent(true);
        }
        try {
            Message sent = execute(message);
            session.getActiveCardMessageIds().clear();
            session.getActiveCardMessageIds().add(sent.getMessageId());
            session.setScreenKind(UserSession.ScreenKind.TEXT);
        } catch (TelegramApiException exception) {
            cleanupCardMessages(chatId, session);
            if (protectContent) {
                renderProtectedTextScreen(chatId, session, text, keyboard);
            } else {
                renderTextScreen(chatId, session, text, keyboard);
            }
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
            cleanupCardMessages(chatId, session);
            session.setScreenKind(UserSession.ScreenKind.TEXT);
            renderTextScreen(chatId, session, caption, keyboard);
            return;
        }

        try {
            cleanupCardMessages(chatId, session);
            if (keyboard != null) {
                showTransientKeyboard(chatId, session, keyboard, keyboardAnchorText(context));
            } else {
                Integer previousMessageId = session.getMenuMessageId();
                if (previousMessageId != null) {
                    deleteMessageSilently(chatId, previousMessageId);
                    session.setMenuMessageId(null);
                }
                session.getCurrentButtonActions().clear();
                session.setScreenKind(UserSession.ScreenKind.TEXT);
            }
            List<Message> sentMessages = new ArrayList<>();
            if (media.size() == 1) {
                MediaAttachment single = media.getFirst();
                Message sent;
                if (single.getType() == MediaType.PHOTO) {
                    SendPhoto sendPhoto = new SendPhoto();
                    sendPhoto.setChatId(chatId);
                    sendPhoto.setPhoto(new InputFile(single.getFileId()));
                    sendPhoto.setCaption(trimCaption(caption));
                    sendPhoto.setParseMode(ParseMode.HTML);
                    sent = execute(sendPhoto);
                } else {
                    SendVideo sendVideo = new SendVideo();
                    sendVideo.setChatId(chatId);
                    sendVideo.setVideo(new InputFile(single.getFileId()));
                    sendVideo.setCaption(trimCaption(caption));
                    sendVideo.setParseMode(ParseMode.HTML);
                    sent = execute(sendVideo);
                }
                sentMessages.add(sent);
            } else {
                SendMediaGroup sendMediaGroup = new SendMediaGroup();
                sendMediaGroup.setChatId(chatId);

                List<InputMedia> album = new ArrayList<>();
                for (int index = 0; index < media.size(); index++) {
                    String mediaCaption = index == 0 ? trimCaption(caption) : null;
                    album.add(buildInputMedia(media.get(index), mediaCaption));
                }
                sendMediaGroup.setMedias(album);
                sentMessages = execute(sendMediaGroup);
            }

            session.getActiveCardMessageIds().clear();
            for (Message sentMessage : sentMessages) {
                session.getActiveCardMessageIds().add(sentMessage.getMessageId());
            }
        } catch (TelegramApiException exception) {
            cleanupCardMessages(chatId, session);
            renderTextScreen(chatId, session, caption, keyboard);
        }
    }

    private void showTransientKeyboard(long chatId, UserSession session, InlineKeyboardMarkup keyboard, String anchorText) {
        ReplyKeyboardMarkup replyKeyboard = replyKeyboardFromInline(session, keyboard);
        if (replyKeyboard == null) {
            return;
        }

        Integer previousMessageId = session.getMenuMessageId();
        if (previousMessageId != null) {
            deleteMessageSilently(chatId, previousMessageId);
            session.setMenuMessageId(null);
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(anchorText == null || anchorText.isBlank() ? "✨" : anchorText);
        message.setReplyMarkup(replyKeyboard);
        try {
            Message sent = execute(message);
            session.setMenuMessageId(sent.getMessageId());
            session.setScreenKind(UserSession.ScreenKind.TEXT);
        } catch (TelegramApiException exception) {
            throw new IllegalStateException("Unable to attach keyboard", exception);
        }
    }

    private String keyboardAnchorText(ProfileScreenContext context) {
        return switch (context) {
            case MY_PROFILE -> "✨ 👤";
            case BROWSE -> "✨ 🔎";
            case LIKES -> "✨ 💌";
            case MODERATION_REPORT -> "✨ 🛡";
            case DRAFT_PREVIEW -> "✨ ✅";
            case NONE -> "✨";
        };
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

    private void addTelegramProfilePhoto(long chatId, UserSession session, UserAccount account) {
        if (session.getDraft() == null) {
            openHome(chatId, account, session, "No active media editor. Start again.");
            return;
        }
        if (!session.getDraft().canAddPhoto()) {
            renderWizard(chatId, session, "Photo limit reached. You can keep up to 3 photos, or 1 video plus 2 photos.");
            return;
        }

        GetUserProfilePhotos request = new GetUserProfilePhotos();
        request.setUserId(account.getUserId());
        request.setLimit(1);
        try {
            UserProfilePhotos photos = execute(request);
            if (photos == null || photos.getPhotos() == null || photos.getPhotos().isEmpty() || photos.getPhotos().getFirst().isEmpty()) {
                renderWizard(chatId, session, "Your Telegram profile does not have a photo that I can use right now.");
                return;
            }
            List<PhotoSize> firstSet = photos.getPhotos().getFirst();
            session.getDraft().addPhoto(largestPhoto(firstSet).getFileId());
            renderWizard(chatId, session, "Telegram profile photo added.");
        } catch (TelegramApiException exception) {
            renderWizard(chatId, session, "I could not load your Telegram profile photo right now. Try sending a photo manually.");
        }
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

    private boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.startsWith("https://") || normalized.startsWith("http://");
    }

    private void cancelWizard(long chatId, UserSession session, UserAccount account) {
        session.setDraft(null);
        session.setExpectedInput(ExpectedInput.NONE);
        clearWizardUi(chatId, session, true);
        openHome(chatId, account, session, "Profile setup cancelled.");
    }

    private boolean isBackChoice(String text) {
        return matchesChoice(text, "Back");
    }

    private boolean isCancelChoice(String text) {
        return matchesChoice(text, "Cancel");
    }

    private boolean matchesChoice(String text, String label) {
        if (text == null || label == null) {
            return false;
        }
        String normalizedText = text.replaceAll("^[^\\p{L}\\p{N}]+\\s*", "").trim();
        String normalizedLabel = label.trim();
        return normalizedText.equalsIgnoreCase(normalizedLabel);
    }

    private Optional<Country> findCountryFromReply(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalizedText = text.trim();
        return profileService.getSortedCountries().stream()
                .filter(country -> normalizedText.equalsIgnoreCase(country.code())
                        || normalizedText.equalsIgnoreCase(country.name())
                        || normalizedText.equalsIgnoreCase(country.flag() + " " + country.name()))
                .findFirst();
    }

    private UserAccount resolveAccountForChat(long chatId) {
        UserAccount account = profileService.getAccount(chatId);
        if (account != null) {
            return account;
        }
        UserAccount fallback = new UserAccount();
        fallback.setUserId(chatId);
        return fallback;
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
            rows.add(List.of(ButtonSpec.callback("View profiles 👀", "menu:search")));
            rows.add(List.of(ButtonSpec.callback("👤 My profile", "menu:profile"), ButtonSpec.callback("💌 Who likes me", "menu:likes")));
            rows.add(List.of(ButtonSpec.callback("💎 Disable ads", "menu:ads")));
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
                List.of(ButtonSpec.callback("View profiles 👀", "menu:search")),
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

    private InlineKeyboardMarkup keyboardDraftPreview() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("✅ Publish profile", "wizard:publish")),
                List.of(ButtonSpec.callback("✏️ Edit media", "wizard:editPreview"), ButtonSpec.callback("⬅️ Back", "wizard:back")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
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
        if (!draft.getMedia().isEmpty()) {
            rows.add(List.of(ButtonSpec.callback("↩️ Remove last", "wizard:mediaRemove"), ButtonSpec.callback("🧹 Clear all", "wizard:mediaClear")));
        }
        rows.add(List.of(ButtonSpec.callback("✅ Preview profile", "wizard:save")));
        rows.add(List.of(ButtonSpec.callback("⬅️ Back", "wizard:back"), ButtonSpec.callback("↩️ Cancel", "wizard:cancel")));
        return uiFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup keyboardBrowse() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("❤️ Like", "browse:like"), ButtonSpec.callback("🚩 Report", "browse:report")),
                List.of(ButtonSpec.callback("⏮ Back", "browse:back"), ButtonSpec.callback("⏭ Skip", "browse:skip")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardAfterBrowseEmpty() {
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("View profiles 👀", "menu:search")),
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
                List.of(ButtonSpec.callback("💎 Disable ads", "menu:ads")),
                List.of(ButtonSpec.callback("⏮ Back", "browse:back"), ButtonSpec.callback("⏭ Skip", "browse:continueAd")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardAdsgramInterstitial(AdsgramAd ad) {
        List<List<ButtonSpec>> rows = new ArrayList<>();
        rows.add(List.of(ButtonSpec.callback("💎 Disable ads", "menu:ads")));
        rows.add(List.of(ButtonSpec.callback("⏮ Back", "browse:back"), ButtonSpec.callback("⏭ Skip", "browse:continueAd")));
        rows.add(List.of(ButtonSpec.callback("🏠 Home", "home")));
        return uiFactory.keyboard(rows);
    }

    private InlineKeyboardMarkup keyboardAdsgramTest(AdsgramAd ad) {
        List<List<ButtonSpec>> rows = new ArrayList<>();
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
        if (add) {
            return uiFactory.keyboard(List.of(
                    List.of(ButtonSpec.callback("👥 Show users", "mod:addAdmin:page:0")),
                    List.of(ButtonSpec.callback("⬅️ Back", "mod:mods")),
                    List.of(ButtonSpec.callback("🏠 Home", "home"))
            ));
        }
        return uiFactory.keyboard(List.of(
                List.of(ButtonSpec.callback("⬅️ Back", "mod:mods")),
                List.of(ButtonSpec.callback("🏠 Home", "home"))
        ));
    }

    private InlineKeyboardMarkup keyboardAdminPicker(List<UserAccount> users, int page, int totalPages) {
        List<List<ButtonSpec>> rows = new ArrayList<>();
        for (UserAccount user : users) {
            String label = adminUserLabel(user);
            rows.add(List.of(ButtonSpec.callback(label, "mod:addAdmin:pick:" + user.getUserId())));
        }

        List<ButtonSpec> pager = new ArrayList<>();
        if (page > 0) {
            pager.add(ButtonSpec.callback("← Prev", "mod:addAdmin:page:" + (page - 1)));
        }
        pager.add(ButtonSpec.callback((page + 1) + "/" + totalPages, "noop"));
        if (page < totalPages - 1) {
            pager.add(ButtonSpec.callback("Next →", "mod:addAdmin:page:" + (page + 1)));
        }
        rows.add(pager);
        rows.add(List.of(ButtonSpec.callback("⬅️ Back", "mod:mods"), ButtonSpec.callback("🏠 Home", "home")));
        return uiFactory.keyboard(rows);
    }

    private String adminUserLabel(UserAccount user) {
        StringBuilder builder = new StringBuilder();
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            builder.append(user.getFirstName());
        } else if (user.getUsername() != null && !user.getUsername().isBlank()) {
            builder.append("@").append(user.getUsername());
        } else {
            builder.append(user.getUserId());
        }
        if (user.isAdmin()) {
            builder.append(" • admin");
        }
        return builder.length() <= 30 ? builder.toString() : builder.substring(0, 27) + "...";
    }

    private String mark(boolean selected, String label) {
        return selected ? "• " + label : label;
    }
}
