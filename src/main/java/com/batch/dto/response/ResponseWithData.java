package com.batch.dto.response;

import lombok.Data;

@Data
public class ResponseWithData {
    private String code;
    private String  message;
    private Object data;

    public ResponseWithData() {
    }

    public ResponseWithData(String code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
}
