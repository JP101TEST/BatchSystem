package com.batch.service;

import com.batch.dto.response.OrderResponse;
import com.batch.dto.response.ResponseGeneral;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
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
import java.util.regex.Pattern;


@Service
public class UploadService {

    private static final String[] ALLOWED_DATABASE_TYPE = {
            "sql",
            "nosql"
    };
    private final int LIMIT_LINE = 200;
    private final String LOCAL_DIR = "local";
    private final String BACKUP_DIR = "backup";
    private final String MONGODB_URL = "mongodb://root:root@localhost:27017/";
    private final String MONGODB_DATABASE = "datanosql";
    private final String MONGODB_COLLECTION_ORDER = "order";
    private final ObjectMapper objectMapper;

    public UploadService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

    public Object upload(MultipartFile file, String databaseType) {
        // ตรวจสอบ file ที่รับเข้ามา
        if (file == null || file.isEmpty() || databaseType == null || databaseType.isEmpty()) {
            return new ResponseGeneral(Integer.toString(400), "Please check input from.");
        }
        // ตรวจสอบการตั้งชื่อ file
        if (file.getOriginalFilename().contains("_")){
            return new ResponseGeneral(Integer.toString(400), "File name can't have '_' .");
        }
        // ตรวจสอบประเภท database
        boolean correctType = false;
        for (String type : ALLOWED_DATABASE_TYPE) {
            if (type.equals(databaseType)) {
                correctType = true;
                break;
            }
        }
        if (!correctType) {
            return new ResponseGeneral(Integer.toString(400), "Database type correct not.");
        }
        // แยกเอานามสกุล file กับ ชื่อ
        String[] splitFilename = Objects.requireNonNull(file.getOriginalFilename()).split("\\.");
        String fileTypename = splitFilename[splitFilename.length - 1];
        // ประกาศ jsonNode หรือคือ object ประเภท json
        JsonNode data;
        try {
            // อ่านข้อมูลจาก file upload แล้วแปลงเป็น json แล้วส่งออกไปยัง data โดยต้องมีการส่ง file และนามสกุล file
            data = switchReadFile(file.getInputStream(), fileTypename);
        } catch (Exception e) {
            return new ResponseGeneral(Integer.toString(400), e.getMessage());
        }
        // ตรวจเช็ค backup directory ว่าไม่มีอยู่จริง
        if (!Files.exists(Paths.get(BACKUP_DIR))) {
            // ทำการสร้าง directory
            Paths.get(BACKUP_DIR).toFile().mkdir();
        }
        // ประกาศแหล่งที่อยู่ของ backup directory ณ วันที่ upload
        Path directoryBackupThisDay = Paths.get(BACKUP_DIR, getDateNowToString());
        // ประกาศแหล่งที่อยู่ของ backup directory ณ วันที่ upload ที่ใช้เก็บ origin file
        Path directoryOrigen = Paths.get(directoryBackupThisDay.toString(), "origin");
        // ตรวจสอบว่า backup directory ณ วันที่ upload ว่าไม่มีอยู่จริง
        if (!Files.exists(directoryBackupThisDay)) {
            // ทำการสร้าง directory
            directoryBackupThisDay.toFile().mkdir();
        }
        // ตรวจสอบว่า backup directory ณ วันที่ upload ที่ใช้เก็บ origin file ว่าไม่มีอยู่จริง
        if (!Files.exists(directoryOrigen)) {
            // ทำการสร้าง directory
            directoryOrigen.toFile().mkdir();
        }
        // ตรวจเช็ค file ซ้ำ
        System.out.println(directoryOrigen.toString());
        String newFileName = databaseType + "_" + file.getOriginalFilename();
        try {
            List<String> fileList = Arrays.asList(directoryOrigen.toFile().list());
            for (int index = 0; index < fileList.size(); index++) {
                if (fileList.get(index).equals(databaseType + "_" + file.getOriginalFilename())) {
                    int numberFile = 1;
                    String[] fileName = file.getOriginalFilename().split("\\.");
                    while (true) {
                        // System.out.println(fileName[0] + "_" + Integer.toString(numberFile) + "." + fileName[1]);
                        if (!Paths.get(directoryOrigen.toString(), databaseType + "_" +fileName[0] + "_" + Integer.toString(numberFile) + "." + fileName[1]).toFile().exists()) {
                            newFileName = databaseType + "_" + fileName[0] + "_" + Integer.toString(numberFile) + "." + fileName[1];
                            break;
                        }
                        numberFile++;
                    }
                    break;
                }
            }
        } catch (Exception e) {
            return new ResponseGeneral(Integer.toString(400), "Can't get files.");
        }
        // ประกาศ file ที่ต้องการ upload ไปเก็บไว้ใน backup origin directory
        Path originFile = Paths.get(directoryOrigen.toString(), newFileName);
        // System.out.println("Origin file name : " + originFile.toString());
        //return new ResponseGeneral(Integer.toString(100), "Test");
        try {
            // ทำการสร้าง file โดยการ transfer ข้อมูลจาก file ที่รับเข้ามาจาก api เข้าไปใน file ที่ต้องการเก็บ
            file.transferTo(originFile);
        } catch (Exception e) {
            return e.getMessage();
        }
        // ประกาศแหล่ง backup directory ณ วันที่ upload ใช้เก็บ file ที่ได้รับการแบ่งออกจาก file origin
        Path backupStoreForFileSplit = Paths.get(directoryBackupThisDay.toString(), "file");
        // ประกาศแหล่ง local directory ณ วันที่ upload ใช้เก็บ file ที่ได้รับการแบ่งออกจาก file origin
        Path directoryLocalThisDay = Paths.get(LOCAL_DIR, getDateNowToString());
        // ตรวจสอบว่า backup directory ณ วันที่ upload ใช้เก็บ file ที่ได้รับการแบ่งออกจาก file origin ว่าไม่มีอยู่จริง
        if (!Files.exists(backupStoreForFileSplit)) {
            // ทำการสร้าง directory
            backupStoreForFileSplit.toFile().mkdir();
        }
        // ตรวจสอบว่า local directory ว่าไม่มีอยู่จริง
        if (!Files.exists(Paths.get(LOCAL_DIR))) {
            // ทำการสร้าง directory
            Paths.get(LOCAL_DIR).toFile().mkdir();
        }
        // ตรวจสอบว่า local directory ณ วันที่ upload ว่าไม่มีอยู่จริง
        if (!Files.exists(directoryLocalThisDay)) {
            // ทำการสร้าง directory
            directoryLocalThisDay.toFile().mkdir();
        }
        // ประกาศ list ใช้เก็บแหล่งที่อยู๋ file ที่เกิดจากการแบ่งโดยจะนำไปใช้สำหรับทำการ batch
        List<Path> locationFileToProcess = new ArrayList<>();
        // ประกาศหมายเลขหลังชื่อ file ที่เกิดจากการแบ่งโดยเริ่มที่ 1 ตัวอย่าง original.json เมื่อแบ่งจะได้ original_1.txt , original_2.txt , ... , original_n.txt
        int fileNumber = 1;
        // ประการ list ใช้เก็บ object ที่อ่านได้จาก data
        List<JsonNode> dataForNewFile = new ArrayList<>();
        // loop รับ object array จาก data
        for (JsonNode dataLine : data) {
            // นำ object ที่ได้ไปเก็บใน dataForNewFile
            dataForNewFile.add(dataLine);
            // ตรวจสอบว่า dataForNewFile ขนาดข้อมูลตามที่กำหนด
            if (dataForNewFile.size() == LIMIT_LINE) {
                String[] newName = newFileName.split("\\.");
                // ประกาศตำแหน่ง file ที่เกิดจากการแบ่งทั้ง local และ backup
                Path createFileForBackup = Paths.get(backupStoreForFileSplit.toString(), newName[0]+ "_path_" + fileNumber + ".txt");
                Path createFileForLocal = Paths.get(directoryLocalThisDay.toString(), newName[0] + "_path_" + fileNumber + ".txt");
                // เอาแหล่ง file ที่เกิดจากการแบ่งทั้ง local ไปเก็บไว้ใน locationFileToProcess
                locationFileToProcess.add(createFileForLocal);
                // สร้าง file ที่เกิดจากการแบ่งจาก dataForNewFile
                writeDataToFile(dataForNewFile, createFileForBackup);
                writeDataToFile(dataForNewFile, createFileForLocal);
                fileNumber++;
                dataForNewFile.clear();
            }
        }
        // #### update to mongodb ####
        // Connect to MongoDB
        MongoClient mongoClient = MongoClients.create(MONGODB_URL);
        // Get the database
        // ถ้าหาก database ไม่พบจะสร้างเองอัตโนมัติ
        MongoDatabase database = mongoClient.getDatabase(MONGODB_DATABASE);
        // Get the collection ไม่พบจะสร้างเองอัตโนมัติ
        MongoCollection<Document> collection = database.getCollection(MONGODB_COLLECTION_ORDER);
        // loop locationFileToProcess เพื่อ insert ลำดับการอ่าน file
        for (Path order : locationFileToProcess) {
            try {
                // สร้าง object สำหรับสร้าง document
                OrderResponse orderResponse = new OrderResponse(order.toString(), databaseType, Files.readAttributes(order, BasicFileAttributes.class).creationTime().toString());
                // สร้าง document ที่เกิดจการการ convert orderResponse
                Document orderInMongo = new Document(objectMapper.convertValue(orderResponse, Document.class));
                // ทำการ insert
                collection.insertOne(orderInMongo);
            } catch (Exception e) {
                return new ResponseGeneral(Integer.toString(400), "Can't get create time.");
            }
        }
        // Close connect
        mongoClient.close();
        return new ResponseGeneral(Integer.toString(200), "Upload successful.");
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

            // อ่านแถวแรกหรือก็คือส่วน header
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

            // อ่านแถวแรกหรือก็คือส่วน header
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

}

