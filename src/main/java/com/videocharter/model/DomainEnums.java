package com.videocharter.model;

public final class DomainEnums {

    private DomainEnums() {
    }

    public enum Gender {
        MALE("Male"),
        FEMALE("Female"),
        OTHER("Other");

        private final String label;

        Gender(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum PartnerPreference {
        MEN("Men"),
        WOMEN("Women"),
        EVERYONE("Everyone");

        private final String label;

        PartnerPreference(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Goal {
        DATING("Dating"),
        FRIENDSHIP("Friendship"),
        LANGUAGE_EXCHANGE("Language exchange"),
        NETWORKING("Networking");

        private final String label;

        Goal(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum PrivacyMode {
        OPEN("Open"),
        PRIVATE("Private");

        private final String label;

        PrivacyMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum MediaType {
        PHOTO,
        VIDEO
    }

    public enum ReportReason {
        SPAM("Spam"),
        FAKE_PROFILE("Fake profile"),
        INAPPROPRIATE_CONTENT("Inappropriate content"),
        UNDERAGE("Underage"),
        HARASSMENT("Harassment"),
        OTHER("Other");

        private final String label;

        ReportReason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum ReportStatus {
        OPEN,
        APPROVED,
        REJECTED
    }
}
