package com.batch.service;

import com.batch.dto.response.ResponseGeneral;
import com.batch.dto.response.ResponseWithData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.*;

@Service
public class SearchService {

    private final String MONGODB_URL = "mongodb://root:root@localhost:27017/";
    private final String MONGODB_DATABASE = "datanosql";
    private final String MONGODB_COLLECTION_PERSON = "person";

    private final ObjectMapper objectMapper;

    public SearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Object readAll() {

        // Connect to MongoDB
        MongoClient mongoClient = MongoClients.create("mongodb://root:root@localhost:27017/");
        // Get the database
        MongoDatabase database = mongoClient.getDatabase("datanosql");
        // Get the collection
        MongoCollection<Document> collection = database.getCollection("person");
        // Find document
        MongoCursor<Document> mongoCursor = collection.find(Filters.empty()).sort(Sorts.descending("username")).iterator();

        if (mongoCursor == null) {
            return new ResponseGeneral(Integer.toString(400), "Collection is empty.");
        }

        List<Document> documentList = new ArrayList<>();

        while (mongoCursor.hasNext()) {
            documentList.add(mongoCursor.next());
        }

        for (Document child : documentList) {
            System.out.println(child);
        }

        return new ResponseWithData(Integer.toString(200), "Successful.", documentList);
    }

    public Object getAllOrderError() {
        // Connect to MongoDB
        MongoClient mongoClient = MongoClients.create(MONGODB_URL);
        // Get the database
        MongoDatabase database = mongoClient.getDatabase(MONGODB_DATABASE);
        // Get the collection
        MongoCollection<Document> collectionOrder = database.getCollection("order");

        MongoCursor<Document> cursor = collectionOrder.find(Filters.exists("errors", true)).iterator();

        List<Document> allOrderDocument = new ArrayList<>();
        while (cursor.hasNext()) {
            Document document = cursor.next();
            document.remove("_id");
            allOrderDocument.add(document);
        }
        return new ResponseWithData(Integer.toString(200), "Successful.", allOrderDocument);
    }

    public Object search(String req) {
        Map<String, Object> searchRequest;
        try {
            searchRequest = objectMapper.readValue(req, HashMap.class);
        } catch (Exception e) {
            return new ResponseGeneral(Integer.toString(400), "Request not correct pattern.");
        }
        if (searchRequest.get("page") == null){
            return new ResponseGeneral(Integer.toString(400), "Request mush have field page.");
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
        long sizeLikePaginateCount = 0;

        int pageSize = 3;
        int currentPage = 1;
        // Perform the searches
        for (int index = 0; index < 2; index++) {
            MongoCursor<Document> mongoCursor;
            MongoCursor<Document> size;
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
                size = collection.find(searchLike).iterator();

                while (size.hasNext()) {
                    sizeLikePaginateCount++;
                    System.out.println(size.next().toString());
                }
                System.out.println(sizeLikePaginateCount);
                // ตรวจสอบหมายเลขหน้า (page) จาก searchRequest
                int page = 0;
                if (searchRequest.get("page") != null) {
                    int requestedPage = (int) searchRequest.get("page");
                    if (requestedPage > 1) {
                        page = (requestedPage - 1) * pageSize;
                    } else {
                        page = 0;
                    }
                }
                // ทำ  ด้วย search like
                // limit คือ การกำหนด จำนวน document ที่ต้องการจากการหาทั้งหมด
                // skip คือ index เริ่มต้นโดยจะเริ่มจาก 0
                mongoCursor = collection.find(searchLike).limit(3).skip(page).iterator();
                currentPage = page;
                while (mongoCursor.hasNext()) {
                    like.add(mongoCursor.next());
                    // ลบ field "_id"
                    like.getLast().remove("_id");
                }
            }
        }
        data.put("equal", equal);
        data.put("like", like);
        data.put("like_count_item_per_page", like.size());
        data.put("like_count_item", sizeLikePaginateCount);
        // ปัดเศษ
        data.put("like_total_page", (int) Math.ceil((double) sizeLikePaginateCount / 3));
        if (currentPage < 1){
            currentPage =1;
        }
        data.put("page_current", currentPage );

        return new ResponseWithData(Integer.toString(200), "Successful.", data);

    }
}
