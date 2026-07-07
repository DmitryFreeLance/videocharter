package com.videocharter.model;

import com.videocharter.model.DomainEnums.ReportReason;
import com.videocharter.model.DomainEnums.ReportStatus;
import java.time.Instant;

public class ReportRecord {

    private long id;
    private long reporterUserId;
    private long targetUserId;
    private ReportReason reason;
    private String evidenceText;
    private String evidenceFileId;
    private String evidenceFileType;
    private Instant createdAt;
    private ReportStatus status = ReportStatus.OPEN;
    private Long moderatorId;
    private String decisionNote;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getReporterUserId() {
        return reporterUserId;
    }

    public void setReporterUserId(long reporterUserId) {
        this.reporterUserId = reporterUserId;
    }

    public long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public void setReason(ReportReason reason) {
        this.reason = reason;
    }

    public String getEvidenceText() {
        return evidenceText;
    }

    public void setEvidenceText(String evidenceText) {
        this.evidenceText = evidenceText;
    }

    public String getEvidenceFileId() {
        return evidenceFileId;
    }

    public void setEvidenceFileId(String evidenceFileId) {
        this.evidenceFileId = evidenceFileId;
    }

    public String getEvidenceFileType() {
        return evidenceFileType;
    }

    public void setEvidenceFileType(String evidenceFileType) {
        this.evidenceFileType = evidenceFileType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public Long getModeratorId() {
        return moderatorId;
    }

    public void setModeratorId(Long moderatorId) {
        this.moderatorId = moderatorId;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }
}
