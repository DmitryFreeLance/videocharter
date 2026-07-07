package com.videocharter.model;

import com.videocharter.model.DomainEnums.Gender;
import com.videocharter.model.DomainEnums.Goal;
import com.videocharter.model.DomainEnums.PartnerPreference;
import com.videocharter.model.DomainEnums.PrivacyMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class UserProfile {

    private long userId;
    private String username;
    private String name;
    private Gender gender;
    private PartnerPreference lookingFor;
    private Goal goal;
    private int age;
    private int preferredAgeMin;
    private int preferredAgeMax;
    private PrivacyMode privacyMode;
    private String countryCode;
    private String countryName;
    private String countryFlag;
    private List<MediaAttachment> media = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public int getPreferredAgeMin() {
        return preferredAgeMin;
    }

    public void setPreferredAgeMin(int preferredAgeMin) {
        this.preferredAgeMin = preferredAgeMin;
    }

    public int getPreferredAgeMax() {
        return preferredAgeMax;
    }

    public void setPreferredAgeMax(int preferredAgeMax) {
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
