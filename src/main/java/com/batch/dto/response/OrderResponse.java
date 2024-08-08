package com.batch.dto.response;

import lombok.Data;

@Data
public class OrderResponse {
    private String fileLocation;
    private String databaseType;
    private long startReadLine;
    private String createTime;

    public OrderResponse() {
    }

    public OrderResponse(String fileLocation, String databaseType, String createTime) {
        this.fileLocation = fileLocation;
        this.databaseType = databaseType;
        this.startReadLine = 0;
        this.createTime = createTime;
    }
}
