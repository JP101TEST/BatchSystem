package com.batch.dto.response;

import lombok.Data;

@Data
public class OrderResponse {
    private String fileLocation;
    private String databaseType;
    private String entityType;
    private long startReadLine;
    private String createTime;

    public OrderResponse() {
    }

    public OrderResponse(String fileLocation, String databaseType, String createTime) {
        this.fileLocation = fileLocation;
        this.databaseType = databaseType;
        this.entityType = null;
        this.startReadLine = 0;
        this.createTime = createTime;
    }
    public OrderResponse(String fileLocation, String databaseType,String entityType, String createTime) {
        this.fileLocation = fileLocation;
        this.databaseType = databaseType;
        this.entityType = entityType;
        this.startReadLine = 0;
        this.createTime = createTime;
    }
}
