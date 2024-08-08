package com.batch.service;

import com.batch.dto.response.OrderResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.mongodb.client.*;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.Data;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Service
public class UploadService {

    private final int LIMIT_LINE = 200;
    private final String LOCAL_DIR = "local";
    private final String BACKUP_DIR = "backup";
    private static final String[] ALLOWED_TYPE = {
            "sql",
            "nosql"
    };
    private final ObjectMapper objectMapper;

    public UploadService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }





    public Object upload(MultipartFile file, String databaseType) {
        System.out.println("File : " + file.getOriginalFilename() + " |#| Database type : " + databaseType);

        // ตรวจสอบ input
        if (file == null || file.isEmpty() || databaseType == null || databaseType.isEmpty()) {
            return "Please check input from.";
        }

        boolean correctType = false;
        for (String type : ALLOWED_TYPE){
            if (type.equals(databaseType)){
                correctType = true;
                break;
            }
        }
        if (!correctType){
            return "Database type correct not.";
        }

        // แยกเอา file type
        String[] splitFilename = Objects.requireNonNull(file.getOriginalFilename()).split("\\.");
        String filename = splitFilename[0];
        String fileTypename = splitFilename[splitFilename.length - 1];

        // เตรียมตัวแปรรับค่าจากการอ่าน file
        JsonNode data;

        // อ่าน file
        try {
            data = switchReadFile(file.getInputStream(), fileTypename);
        } catch (Exception e) {
            return e.getMessage();
        }

        //#### ทำการเอา file ไปเก็บใน backup ####

        // ตรวจเช็ค backup directory
        if (!Files.exists(Paths.get(BACKUP_DIR))) {
            Paths.get(BACKUP_DIR).toFile().mkdir();
        }

        // ทำการประกาศแหล่งที่อยู่ของ backup directory ณ วันที่ upload
        Path directoryBackupThisDay = Paths.get(BACKUP_DIR, getDateNowToString());
        Path directoryOrigen = Paths.get(directoryBackupThisDay.toString(), "origin");
        if (!Files.exists(directoryBackupThisDay)) {
            directoryBackupThisDay.toFile().mkdir();
        }
        if (!Files.exists(directoryOrigen)) {
            directoryOrigen.toFile().mkdir();
        }

        // upload file
        Path originFile = Paths.get(directoryOrigen.toString(), file.getOriginalFilename());
        try {
            file.transferTo(originFile);
        } catch (Exception e) {
            return e.getMessage();
        }

        // ทำการแบ่ง file สำหรับการ insert
        Path backupStoreForFileSplit = Paths.get(directoryBackupThisDay.toString(),"file");
        // ทำการประกาศแหล่งที่อยู่ของ local directory ณ วันที่ upload
        Path directoryLocalThisDay = Paths.get(LOCAL_DIR, getDateNowToString());
        // ตรวจเช็ค local directory
        if (!Files.exists(backupStoreForFileSplit)){
            backupStoreForFileSplit.toFile().mkdir();
        }
        if (!Files.exists(Paths.get(LOCAL_DIR))) {
            Paths.get(LOCAL_DIR).toFile().mkdir();
        }
        if (!Files.exists(directoryLocalThisDay)) {
            directoryLocalThisDay.toFile().mkdir();
        }

        List<Path> locationFileToProcess = new ArrayList<>();

        int fileNumber = 1;
        long indexLine = 0;
        List<JsonNode> dataForNewFile = new ArrayList<>();
        for (JsonNode dataLine : data) {
            dataForNewFile.add(dataLine);
            if (indexLine == LIMIT_LINE) {
                Path createFileForBackup = Paths.get( backupStoreForFileSplit.toString(), databaseType+ "_" +filename + "_" + fileNumber + ".txt");
                Path createFileForLocal = Paths.get(directoryLocalThisDay.toString(), databaseType+ "_" +filename + "_" + fileNumber + ".txt");
                locationFileToProcess.add(createFileForLocal);
                writeDataToFile(dataForNewFile, createFileForBackup);
                writeDataToFile(dataForNewFile, createFileForLocal);
                fileNumber++;
                indexLine = 0;
                dataForNewFile.clear();
            }
            indexLine++;
        }

        // #### update to mongodb ####

        // Connect to MongoDB
        MongoClient mongoClient = MongoClients.create("mongodb://root:root@localhost:27017/");
        // Get the database
        MongoDatabase database = mongoClient.getDatabase("datanosql");
        // Get the collection
        MongoCollection<Document> collection = database.getCollection("order");

        for (Path order : locationFileToProcess){
            try {
                OrderResponse orderResponse = new OrderResponse(order.toString(),databaseType,Files.readAttributes(order, BasicFileAttributes.class).creationTime().toString());
                Document orderInMongo = new Document(objectMapper.convertValue(orderResponse,Document.class));
                collection.insertOne(orderInMongo);
            }catch (Exception e){
                return "Can't get create time.";
            }
        }

        // แสดง document ที่่มีใน collection
        List<Document> allDocument = new ArrayList<>();
        try {
            MongoCursor<Document> cursor = collection.find().iterator();
            while (cursor.hasNext()) {
                Document document = cursor.next();
                document.remove("_id");
                allDocument.add(document);
            }
        } catch (Exception e) {
            return "Can't get document form mongodb.";
        }

        System.out.println(allDocument);

        // Close connect
        mongoClient.close();

        try {
            return locationFileToProcess;
        } catch (Exception e) {
            return "Upload file done.";
        }

        //return "Upload file done.";
    }

    private String getDateNowToString() {
        LocalDate localDate = LocalDate.now();
        String date = localDate.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"));
        return "Date" + date;
    }

    // ### Read File ###
    private JsonNode switchReadFile(InputStream file, String fileTypename) throws Exception {
        JsonNode result = null;
        switch (fileTypename) {
            case "csv":
                result = getCsvDataAndConvertToJsonNode(file);
                break;
            case "xlsx":
                result = getXlsxDataAndConvertToJsonNode(file);
                break;
            case "txt":
                result = objectMapper.readTree(file);
                break;
            case "json":
                result = objectMapper.readTree(file);
                break;
            case "xml":
                XmlMapper xmlMapper = new XmlMapper();
                JsonNode jsonFromXml = xmlMapper.readTree(file);
                Iterator<Map.Entry<String, JsonNode>> fields = jsonFromXml.fields();
                String key = fields.next().getKey();
                result = jsonFromXml.get(key);
                break;
            default:
                throw new CustomException("File type not correct.");
        }
        if (result == null) {
            throw new CustomException("Read data is empty please check you file.");
        }
        return result;
    }

    private JsonNode getCsvDataAndConvertToJsonNode(InputStream file) {
        JsonNode result = null;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(file));
        try {
            CSVReader csvReader = new CSVReaderBuilder(bufferedReader)
                    .withCSVParser(new CSVParserBuilder().withSeparator(',').build())
                    .build();

            // อ่านแถวแรก (header)
            String[] header = csvReader.readNext();

            if (header == null) {
                System.out.println("No data found in CSV file.");
                return result;
            }

            List<JsonNode> allRows = new ArrayList<>();

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                Map<String, Object> data = new HashMap<>();
                for (int i = 0; i < header.length; i++) {
                    if (i < row.length && !row[i].isEmpty()) {
                        String key = header[i];
                        Object value = parseValue(row[i]);
                        if (value != null) {
                            // Place value in correct nested object
                            putValue(data, key, value);
                        }
                    }
                }
                allRows.add(objectMapper.convertValue(data, JsonNode.class));
            }

            result = objectMapper.convertValue(allRows, JsonNode.class);
        } catch (IOException | CsvValidationException e) {
            System.out.println(e);
        }
        return result;
    }

    private JsonNode getXlsxDataAndConvertToJsonNode(InputStream file) {
        JsonNode result = null;
        try (
                Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0); // อ่านแผ่นงานแรก
            Iterator<Row> rowIterator = sheet.iterator();

            // อ่านแถวแรก (header)
            Row headerRow = rowIterator.next();
            String[] header = new String[headerRow.getLastCellNum()];
            for (int i = 0; i < header.length; i++) {
                Cell cell = headerRow.getCell(i);
                header[i] = cell != null ? cell.toString() : "";
            }

            if (header == null) {
                System.out.println("No data found in xlsx file.");
                return result;
            }

            List<JsonNode> allRows = new ArrayList<>();
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Map<String, Object> data = new HashMap<>();
                for (int i = 0; i < header.length; i++) {
                    Cell cell = row.getCell(i);
                    if (cell != null) {
                        String key = header[i];
                        Object value = parseValue(cell.toString());
                        if (value != null) {
                            putValue(data, key, value);
                        }
                    }
                }
                allRows.add(objectMapper.convertValue(data, JsonNode.class));
            }
            result = objectMapper.convertValue(allRows, JsonNode.class);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static Object parseValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        // แปลงเป็นชนิดข้อมูลปกติ
        try {
            // พยายามแปลงเป็น Integer
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // ถ้าไม่ใช่ Integer, ลองแปลงเป็น Double
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                // ถ้าไม่ใช่ Double, ลองแปลงเป็น Boolean
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                    return Boolean.parseBoolean(value);
                }
                return value;
            }
        }

    }

    private static void putValue(Map<String, Object> map, String key, Object value) {
        // แยกคีย์ออกเป็นส่วน ๆ โดยใช้จุด (.) เป็นตัวแยก
        String[] parts = key.split("\\.");
        Map<String, Object> current = map;
        // วนลูปผ่านแต่ละส่วนของคีย์ ยกเว้นส่วนสุดท้าย
        for (int i = 0; i < parts.length - 1; i++) {
            // ตรวจสอบว่าคีย์ส่วนปัจจุบันมีอยู่ใน Map หรือไม่
            // ถ้าไม่มี หรือไม่ใช่ Map ก็สร้าง Map ใหม่ใส่เข้าไป
            if (!current.containsKey(parts[i]) || !(current.get(parts[i]) instanceof Map)) {
                current.put(parts[i], new HashMap<String, Object>());
            }
            // เปลี่ยน current ให้ชี้ไปที่ Map ที่สร้างใหม่ หรือที่มีอยู่แล้ว
            current = (Map<String, Object>) current.get(parts[i]);
        }
        // ใส่ค่า (value) ลงในคีย์ส่วนสุดท้าย
        current.put(parts[parts.length - 1], value);
    }

    //#### Write file ####

    private static void writeDataToFile(Object data, Path filePath) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // Convert data to JSON string
            String jsonString = objectMapper.writeValueAsString(data);

            // Write JSON string to file
            Files.write(filePath, jsonString.getBytes());

            System.out.println("Data successfully written to " + filePath);
        } catch (IOException e) {
            System.out.println("Error writing data to file: " + e.getMessage());
        }
    }

}

