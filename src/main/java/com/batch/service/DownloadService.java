package com.batch.service;

import com.batch.dto.response.ResponseGeneral;
import com.batch.dto.response.ResponseWithData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Filters.and;

@Service
public class DownloadService {
    private final String MONGODB_URL = "mongodb://root:root@localhost:27017/";
    private final String MONGODB_DATABASE = "datanosql";
    private final String MONGODB_COLLECTION_PERSON = "person";
    private final ObjectMapper objectMapper;
    private final SearchService searchService;

    public DownloadService(ObjectMapper objectMapper, SearchService searchService) {
        this.objectMapper = objectMapper;
        this.searchService = searchService;
    }

    public Object download(String req){
        try {
            Object rep = search(req);
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

    public Object search(String req) {
        Map<String, Object> searchRequest;
        try {
            searchRequest = objectMapper.readValue(req, HashMap.class);
        } catch (Exception e) {
            return new ResponseGeneral(Integer.toString(400), "Request not correct pattern.");
        }

        // Connect to MongoDB
        MongoClient mongoClient = MongoClients.create(MONGODB_URL);
        // Get the database
        MongoDatabase database = mongoClient.getDatabase(MONGODB_DATABASE);
        // Get the collection
        MongoCollection<Document> collection = database.getCollection(MONGODB_COLLECTION_PERSON);

        List<Bson> equalSearch = new ArrayList<>();
        List<Bson> likeSearch = new ArrayList<>();
        List<Document> equal = new ArrayList<>();
        List<Document> like = new ArrayList<>();
        Map<String, Object> data = new HashMap<>();

        for (Map.Entry<String, Object> dataMap : searchRequest.entrySet()) {
            if (dataMap.getKey().equals("page")) {
                continue;
            }
            Object value = dataMap.getValue();
            if (value != null && !value.toString().isEmpty()) {
                System.out.println(value + " |Type " + value.getClass());
                if (value instanceof Number || value instanceof Boolean) {
                    // Handle numbers (e.g., age)
                    equalSearch.add(eq(dataMap.getKey(), value));
                    likeSearch.add(eq(dataMap.getKey(), value));
                } else {
                    // Handle strings (e.g., username, gender)
                    equalSearch.add(eq(dataMap.getKey(), value.toString()));
                    likeSearch.add(regex(dataMap.getKey(), value.toString(), "i"));
                }
            }
        }

        Bson searchEqual = equalSearch.isEmpty() ? new Document() : and(equalSearch);
        Bson searchLike = likeSearch.isEmpty() ? new Document() : and(likeSearch);

        // Perform the searches
        for (int index = 0; index < 2; index++) {
            MongoCursor<Document> mongoCursor;
            if (index == 0) {
                if (searchEqual == null || equalSearch.isEmpty()) {
                    continue;
                }
                mongoCursor = collection.find(searchEqual).iterator();
                while (mongoCursor.hasNext()) {
                    equal.add(mongoCursor.next());
                    equal.getLast().remove("_id");
                }
            } else {
                if (searchLike == null || likeSearch.isEmpty()) {
                    continue;
                }
                // ทำ  ด้วย search like
                mongoCursor = collection.find(searchLike).iterator();
                while (mongoCursor.hasNext()) {
                    like.add(mongoCursor.next());
                    // ลบ field "_id"
                    like.getLast().remove("_id");
                }
            }
        }
        data.put("equal", equal);
        data.put("like", like);

        return new ResponseWithData(Integer.toString(200), "Successful.", data);

    }
}
