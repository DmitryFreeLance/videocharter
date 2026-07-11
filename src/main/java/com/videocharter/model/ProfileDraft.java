package com.videocharter.model;

import com.videocharter.model.DomainEnums.Gender;
import com.videocharter.model.DomainEnums.Goal;
import com.videocharter.model.DomainEnums.MediaType;
import com.videocharter.model.DomainEnums.PartnerPreference;
import com.videocharter.model.DomainEnums.PrivacyMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ProfileDraft {

    public enum WizardStep {
        GENDER,
        LOOKING_FOR,
        GOAL,
        NAME,
        AGE,
        AGE_RANGE,
        PRIVACY,
        COUNTRY,
        MEDIA,
        PREVIEW
    }

    private Gender gender;
    private PartnerPreference lookingFor;
    private Goal goal;
    private String name;
    private Integer age;
    private Integer preferredAgeMin;
    private Integer preferredAgeMax;
    private PrivacyMode privacyMode;
    private String countryCode;
    private String countryName;
    private String countryFlag;
    private List<MediaAttachment> media = new ArrayList<>();
    private WizardStep step = WizardStep.GENDER;

    public static ProfileDraft empty() {
        return new ProfileDraft();
    }

    public static ProfileDraft fromProfile(UserProfile profile) {
        ProfileDraft draft = new ProfileDraft();
        draft.gender = profile.getGender();
        draft.lookingFor = profile.getLookingFor();
        draft.goal = profile.getGoal();
        draft.name = profile.getName();
        draft.age = profile.getAge();
        draft.preferredAgeMin = profile.getPreferredAgeMin();
        draft.preferredAgeMax = profile.getPreferredAgeMax();
        draft.privacyMode = profile.getPrivacyMode();
        draft.countryCode = profile.getCountryCode();
        draft.countryName = profile.getCountryName();
        draft.countryFlag = profile.getCountryFlag();
        draft.media = new ArrayList<>(profile.getMedia());
        draft.step = WizardStep.GENDER;
        return draft;
    }

    public UserProfile toProfile(long userId, String username, UserProfile existing) {
        UserProfile profile = existing == null ? new UserProfile() : existing;
        Instant now = Instant.now();
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(now);
        }
        profile.setUpdatedAt(now);
        profile.setUserId(userId);
        profile.setUsername(username);
        profile.setGender(gender);
        profile.setLookingFor(lookingFor);
        profile.setGoal(goal);
        profile.setName(name);
        profile.setAge(age == null ? 0 : age);
        profile.setPreferredAgeMin(preferredAgeMin == null ? 0 : preferredAgeMin);
        profile.setPreferredAgeMax(preferredAgeMax == null ? 999 : preferredAgeMax);
        profile.setPrivacyMode(privacyMode);
        profile.setCountryCode(countryCode);
        profile.setCountryName(countryName);
        profile.setCountryFlag(countryFlag);
        profile.setMedia(new ArrayList<>(media));
        return profile;
    }

    public UserProfile toPreviewProfile(long userId, String username) {
        return toProfile(userId, username, null);
    }

    public int getPhotoCount() {
        return (int) media.stream().filter(item -> item.getType() == MediaType.PHOTO).count();
    }

    public boolean hasVideo() {
        return media.stream().anyMatch(item -> item.getType() == MediaType.VIDEO);
    }

    public boolean canAddPhoto() {
        if (media.size() >= 3) {
            return false;
        }
        return !hasVideo() || getPhotoCount() < 2;
    }

    public boolean canAddVideo() {
        return !hasVideo() && media.size() < 3;
    }

    public void addPhoto(String fileId) {
        media.add(new MediaAttachment(MediaType.PHOTO, fileId));
    }

    public void addVideo(String fileId) {
        media.add(new MediaAttachment(MediaType.VIDEO, fileId));
    }

    public void removeLastMedia() {
        if (!media.isEmpty()) {
            media.remove(media.size() - 1);
        }
    }

    public void clearMedia() {
        media.clear();
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public PartnerPreference getLookingFor() {
        return lookingFor;
    }

    public void setLookingFor(PartnerPreference lookingFor) {
        this.lookingFor = lookingFor;
    }

    public Goal getGoal() {
        return goal;
    }

    public void setGoal(Goal goal) {
        this.goal = goal;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Integer getPreferredAgeMin() {
        return preferredAgeMin;
    }

    public void setPreferredAgeMin(Integer preferredAgeMin) {
        this.preferredAgeMin = preferredAgeMin;
    }

    public Integer getPreferredAgeMax() {
        return preferredAgeMax;
    }

    public void setPreferredAgeMax(Integer preferredAgeMax) {
        this.preferredAgeMax = preferredAgeMax;
    }

    public PrivacyMode getPrivacyMode() {
        return privacyMode;
    }

    public void setPrivacyMode(PrivacyMode privacyMode) {
        this.privacyMode = privacyMode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public String getCountryFlag() {
        return countryFlag;
    }

    public void setCountryFlag(String countryFlag) {
        this.countryFlag = countryFlag;
    }

    public List<MediaAttachment> getMedia() {
        return media;
    }

    public void setMedia(List<MediaAttachment> media) {
        this.media = media;
    }

    public WizardStep getStep() {
        return step;
    }

    public void setStep(WizardStep step) {
        this.step = step;
    }
}
