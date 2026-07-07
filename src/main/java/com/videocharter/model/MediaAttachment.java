package com.videocharter.model;

import com.videocharter.model.DomainEnums.MediaType;

public class MediaAttachment {

    private MediaType type;
    private String fileId;

    public MediaAttachment() {
    }

    public MediaAttachment(MediaType type, String fileId) {
        this.type = type;
        this.fileId = fileId;
    }

    public MediaType getType() {
        return type;
    }

    public void setType(MediaType type) {
        this.type = type;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }
}
