package com.batch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class DownloadService {
    private final ObjectMapper objectMapper;
    private final SearchService searchService;

    public DownloadService(ObjectMapper objectMapper, SearchService searchService) {
        this.objectMapper = objectMapper;
        this.searchService = searchService;
    }

    public Object download(String req){
        try {
            Object rep = searchService.search(req);
            Map<String,Object> objectMap = objectMapper.convertValue(rep, HashMap.class);
            Object data = objectMap.get("data");
            System.out.println(data);
            // Convert data to JSON string
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            objectMapper.writeValue(outputStream, data);

            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(outputStream.toByteArray());

            // Create headers for the response
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=data.json");
            headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");

            // Create the response entity
            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .contentLength(outputStream.size())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(byteArrayInputStream));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}
