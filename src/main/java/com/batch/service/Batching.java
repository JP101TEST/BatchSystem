package com.batch.service;

import com.batch.entity.mysql.PersonSql;
import com.batch.repository.mysql.PersonSqlRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class Batching {

    private final ObjectMapper objectMapper;
    private final PersonSqlRepository personSqlRepository;
    private final long LIMIT_READ_LINE = 50;

    public Batching(ObjectMapper objectMapper, PersonSqlRepository personSqlRepository) {
        this.objectMapper = objectMapper;
        this.personSqlRepository = personSqlRepository;
    }

    public void batch() {

        // Connect to MongoDB
        MongoClient mongoClient = MongoClients.create("mongodb://root:root@localhost:27017/");
        // Get the database
        MongoDatabase database = mongoClient.getDatabase("datanosql");
        // Get the collection
        MongoCollection<Document> collectionOrder = database.getCollection("order");

        // แสดง document ที่่มีใน collection
        List<Document> allOrderDocument = new ArrayList<>();
        try {
            MongoCursor<Document> cursor = collectionOrder.find().iterator();
            while (cursor.hasNext()) {
                Document document = cursor.next();
//                document.remove("_id");
                allOrderDocument.add(document);
            }
        } catch (Exception e) {
            System.out.println("Can't get document form mongodb.");
        }

        for (Document orderList : allOrderDocument) {
            System.out.println("Order document : "+orderList);
            JsonNode data = null;

            try {
                data = objectMapper.readTree(Paths.get(orderList.getString("fileLocation")).toFile());
                //System.out.println(data);
            } catch (Exception e) {
                System.out.println("Can't read file");
            }

            long startReadLine = 0;
            int indexCurrentRead = 0;

            for (startReadLine = orderList.getLong("startReadLine"); startReadLine < data.size(); startReadLine++) {
                System.out.println("Line data size : " + data.size() +" | startReadLine : "+startReadLine);
                indexCurrentRead++;
                if (indexCurrentRead > LIMIT_READ_LINE){
                    break;
                }

                System.out.println("Data : " + data.get((int) startReadLine));

                try {
                    // ### nosql ###
                    MongoCollection<Document> collectionPerson = database.getCollection("person");
                    Document newDocument = Document.parse(data.get((int) startReadLine).toString());
                    //System.out.println("newDocument : " + newDocument);
                    Document query = new Document("username",newDocument.get("username").toString());
                    Document findDocument = collectionPerson.find(query).first();
                    if (findDocument != null) {
                        // Identify fields to remove
                        Set<String> existingFields = findDocument.keySet();
                        Set<String> updatedFields = newDocument.keySet();

                        // Create $unset document
                        Document unsetFields = new Document();
                        for (String field : existingFields) {
                            if (!updatedFields.contains(field) && !field.equals("_id")) {
                                unsetFields.append(field, "");
                            }
                        }

                        // Perform update
                        if (!unsetFields.isEmpty()) {
                            collectionPerson.updateOne(query, new Document("$unset", unsetFields));
                        }

                        // Update existing fields
                        collectionPerson.updateOne(query, new Document("$set", newDocument), new UpdateOptions().upsert(true));
                    }else {
                        // Insert new document if not found
                        collectionPerson.insertOne(newDocument);
                    }


//                    // ### sql ###
//                    PersonSql personSql = objectMapper.readValue(data.get((int) startReadLine).toString(), PersonSql.class);
//                    //System.out.println("personSql : " + personSql);
//                    Optional<PersonSql> optionalPersonSql = personSqlRepository.findByUsername(personSql.getUsername());
//                    if (optionalPersonSql.isPresent()) {
//                        PersonSql existingPersonSql = optionalPersonSql.get();
//                        // Update the existing entity with the new values from personSql
//                        existingPersonSql.setFirst_name(personSql.getFirst_name());
//                        existingPersonSql.setLast_name(personSql.getLast_name());
//                        existingPersonSql.setGender(personSql.getGender());
//                        // Set other fields as necessary
//                        personSqlRepository.save(existingPersonSql);
//                    } else {
//                        // If no existing entity, save the new one
//                        personSqlRepository.save(personSql);
//                    }

                } catch (Exception e) {
                    System.out.println("Can't map.");
                    throw new RuntimeException(e);
                }

//                if (startReadLine == data.size()) {
//                    try {
//                        Files.delete(Paths.get(orderList.getString("fileLocation")));
//                        collectionOrder.deleteOne(orderList);
//
//                    } catch (Exception e) {
//                        System.out.println("Can't delete file");
//                    }
//                    break;
//                }
//                switch (orderList.getString("databaseType")) {
//                    case "nosql":
//                        MongoCollection<Document> collectionPerson = database.getCollection("order");
//                        Document insertDoc = objectMapper.convertValue(data.get( (int)startReadLine).toString(), Document.class);
//                        collectionPerson.insertOne(insertDoc);
//                        break;
//                    case "sql":
////                        System.out.println(data.get(  (int)startReadLine).toString());
//                        PersonSql insertPerson = null;
//                        try {
//                            insertPerson = objectMapper.readValue(data.toString(), PersonSql.class);
//                            System.out.println(insertPerson );
//                        } catch (Exception e) {
//                            System.out.println("error");
//                        }
//                        System.out.println(insertPerson);
//                        if (personSqlRepository.findByUsername(insertPerson.getUsername()).isEmpty()) {
//                            personSqlRepository.save(insertPerson);
//                        }
//                        break;
//                }
//                if (startReadLine == LIMIT_READ_LINE - 1) {
//                    OrderResponse orderUpdate = objectMapper.convertValue(orderList.toString(), OrderResponse.class);
//                    orderUpdate.setStartReadLine(startReadLine);
//                    Document orderUpdateDoc = new Document(objectMapper.convertValue(orderUpdate, Document.class));
//                    collectionOrder.updateOne(orderList, orderUpdateDoc);
//                }
            }

            System.out.println("End startReadLine : "+startReadLine);
            Bson updateReadLine = new Document("startReadLine",startReadLine);
            Bson updateOperation = new Document("$set",updateReadLine);
            collectionOrder.updateOne(orderList,updateOperation);
            if (startReadLine == data.size()){
                System.out.println("Delete order "+orderList.toString());
                ObjectId documentId = orderList.getObjectId("_id");
                collectionOrder.deleteOne(new Document("_id",documentId));

            }
            break;

        }
        mongoClient.close();
        System.out.println("-------------------------------");
    }
}
