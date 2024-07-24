package com.batch.dto.response;

import lombok.Data;

@Data
public class ResponseWithData {
    private String code;
    private String  message;
    private Object data;
}
