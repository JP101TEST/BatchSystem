package com.batch.dto.response;

import lombok.Data;

@Data
public class ResponseGeneral {
    private String code;
    private String  message;

    public ResponseGeneral() {
    }

    public ResponseGeneral(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
