package com.batch.service;

import com.batch.repository.mongodb.PersonNosqlRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class Batch {
    private static final String LOCAL_STORE_DIR = "localStore";
    private static final String BACKUP_STORE_DIR = "backupStore";
    private static final String[] ALLOWED_FILE_TYPES = {"txt", "csv", "xlsx", "json", "xml"};
    private static final String[] ALLOWED_NAME_FILE_SQL = {"data"};
    private static final String[] ALLOWED_NAME_FILE_NOSQL = {"person", "school"};
    private static final String[] ALLOWED_TYPE = {
            "sql",
            "nosql",
            "inProcess"
    };
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PersonNosqlRepository personNosqlRepository;

    public Batch(PersonNosqlRepository personNosqlRepository) {
        this.personNosqlRepository = personNosqlRepository;
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

    public void batch() throws IOException, CsvValidationException {
        // Get directory
        Path localStore = Paths.get(LOCAL_STORE_DIR);
        Path backUpStore = Paths.get(BACKUP_STORE_DIR);
        Path subLocalDirectory = Paths.get(localStore + "\\" + generateDirectoryNameByDate());

        System.out.println("--------------- Before sort ------------------");
        File[] filesGroup = Paths.get(subLocalDirectory+"\\"+"nosql").toFile().listFiles();
        for (File f : filesGroup){
            BasicFileAttributes attr1 = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
            System.out.println(f.toString()+"| time : "+attr1.creationTime().toString());
        }
        System.out.println("--------------- Sort ------------------");
        if (filesGroup != null) {
            Arrays.sort(filesGroup, new Comparator<File>() {
                @Override
                public int compare(File file1, File file2) {
                    try {
                        Path path1 = file1.toPath();
                        Path path2 = file2.toPath();
                        BasicFileAttributes attr1 = Files.readAttributes(path1, BasicFileAttributes.class);
                        BasicFileAttributes attr2 = Files.readAttributes(path2, BasicFileAttributes.class);
                        return attr1.creationTime().compareTo(attr2.creationTime());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            // Print sorted files
            for (File file : filesGroup) {
                BasicFileAttributes attr1 = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                System.out.println(file.toString()+"| time : "+attr1.creationTime().toString());
            }
        } else {
            System.out.println("No files found in the directory.");
        }
        System.out.println("---------------------------------");

        ////////////////  Clear noise ////////////////

        // Check local store
        if (!Files.exists(localStore)) {
            System.out.println("Local store not found.");
            return;
        }

        // Clear non subdirectory in local
        if (clearNonDirectoryAndCountSubDirectory(localStore) == 0) {
            System.out.println("No sub directory.");
            return;
        }

        // Check sub local empty
        if (!Files.exists(subLocalDirectory)) {
            System.out.println("Sub local store for insert not found.");
            return;
        }

        // Check sub local empty and clear non and non-allowed directory sql nosql
        if (clearNonAndUnAllowedSubDirectory(subLocalDirectory, ALLOWED_TYPE) == 0) {
            System.out.println("Directory in sub local store is empty.");
            return;
        }

        //////////////////////////////////////////////

        // Check back up store
        if (!Files.exists(backUpStore)) {
            backUpStore.toFile().mkdirs();
        }
        // Create subdirectory.
        Path subDirectory = Paths.get(BACKUP_STORE_DIR, subLocalDirectory.toFile().getName());
        // Get path subdirectory for bin
        Path binBackUpStore = Paths.get(subDirectory.toString() + "\\" + "bin");
        // Create subdirectory back up.
        subDirectory.toFile().mkdirs();

        File[] directory = subLocalDirectory.toFile().listFiles();

        ////////////////  Read file ////////////////

        for (File dir : directory) {
            File[] files = dir.listFiles();
            System.out.println("Directory : " + dir.getName());
            if (files != null || files.length != 0) {
                for (File file : files) {
                   /* String[] fields = file.getName().split("\\.");
                    if (fields[0].equalsIgnoreCase("person")) {
                        String fileType = file.getName().toString().substring(file.getName().toString().lastIndexOf('.') + 1);
                        JsonNode people = null;
                        switch (fileType) {
                            case "json":
                                people = objectMapper.readTree(file);
                                System.out.println(people);
                                System.out.println("----- json -----");
                                break;
                            case "xml":
                                XmlMapper xmlMapper = new XmlMapper();
                                JsonNode xmlPeople = xmlMapper.readTree(file);
//                                System.out.println(objectMapper.readTree(xmlPeople.toString()));
//                                for (JsonNode xmlPerson : xmlPeople.get("person")) {
//                                    System.out.println(xmlPerson);
//                                }

                                people = objectMapper.readTree(xmlPeople.get(fields[0]).toString());
                                System.out.println("------  xml  ------");
                                break;
                            case "txt":
                                JsonNode txtPeople = objectMapper.readTree(file);
//                                System.out.println(Files.readString(file.toPath()));
//                                for (JsonNode person : txtPeople) {
//                                    System.out.println(person);
//                                }
                                people = txtPeople;
                                System.out.println("------  txt  ------");
                                break;
                            case "csv":
                                try (FileReader reader = new FileReader(file)) {
                                    var csvReader = new CSVReaderBuilder(reader)
                                            .withCSVParser(new CSVParserBuilder().withSeparator(',').build())
                                            .build();

                                    // อ่านแถวแรก (header)
                                    String[] header = csvReader.readNext();
                                    if (header == null) {
                                        System.out.println("No data found in CSV file.");
                                        return;
                                    }

                                    List<JsonNode> allRows = new ArrayList<>();

                                    String[] row;
                                    while ((row = csvReader.readNext()) != null) {
                                        Map<String, Object> data = new HashMap<>();
                                        for (int i = 0; i < header.length; i++) {
                                            if (i < row.length && !row[i].isEmpty()) {
                                                String key = header[i];
                                                Object value = parseValue(key, row[i]);
                                                if (value != null) {
                                                    // Place value in correct nested object
                                                    putValue(data, key, value);
                                                }
                                            }
                                        }
                                        allRows.add(objectMapper.convertValue(data, JsonNode.class));
                                    }

                                    JsonNode output = objectMapper.convertValue(allRows, JsonNode.class);
//                                    System.out.println(output);
                                    people = output;
                                } catch (IOException | CsvValidationException e) {
                                    e.printStackTrace();
                                }
                                System.out.println("------  csv  ------");
                                break;
                            case "xlsx": // แก้
                                try (FileInputStream fis = new FileInputStream(file);
                                     Workbook workbook = new XSSFWorkbook(fis)) {

                                    Sheet sheet = workbook.getSheetAt(0); // อ่านแผ่นงานแรก
                                    Iterator<Row> rowIterator = sheet.iterator();

                                    // อ่านแถวแรก (header)
                                    Row headerRow = rowIterator.next();
                                    String[] header = new String[headerRow.getLastCellNum()];
                                    for (int i = 0; i < header.length; i++) {
                                        Cell cell = headerRow.getCell(i);
                                        header[i] = cell != null ? cell.toString() : "";
                                    }

                                    // อ่านข้อมูลจากแถวถัดไป
                                    List<JsonNode> allRows = new ArrayList<>();
                                    while (rowIterator.hasNext()) {
                                        Row row = rowIterator.next();
                                        Map<String, Object> data = new HashMap<>();
                                        for (int i = 0; i < header.length; i++) {
                                            Cell cell = row.getCell(i);
                                            if (cell != null) {
                                                String key = header[i];
                                                Object value = parseValue(key, cell.toString());
                                                if (value != null) {
                                                    putValue(data, key, value);
                                                }
                                            }
                                        }
                                        allRows.add(objectMapper.convertValue(data, JsonNode.class));
                                    }
                                    JsonNode output = objectMapper.convertValue(allRows, JsonNode.class);
                                    System.out.println(output);
                                    people = output;
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                System.out.println("------  xlsx  ------");
                                break;
                        }

                        if (people == null) {
                            continue;
                        }

                        // Connect to MongoDB
                        MongoClient mongoClient = MongoClients.create("mongodb://root:root@localhost:27017/");
                        // Get the database
                        MongoDatabase database = mongoClient.getDatabase("datanosql");
                        // Get the collection
                        MongoCollection<Document> collection = database.getCollection("people");

                        for (JsonNode childNode : people) {
                            System.out.println(childNode.toString());

                            // Convert JsonNode to Document
                            Document document = Document.parse(childNode.toString());

                            // Check for duplicate first_name and update if exists
                            String firstName = document.getString("first_name");
                            Document query = new Document("first_name", firstName);

                            // Check field
                            if (document.get("interests") != null) {
                                System.out.println(firstName + " have field interests : " + document.get("interests"));
                            }

                            // Clear field
//                            if (document.get("age") != null) {
//                                document.remove("age");
//                            }

//                            collection.insertOne(document);
//                            // Update or insert the document
//                            collection.updateOne(query, new Document("$set", document), new UpdateOptions().upsert(true));
                            // Fetch existing document from the collection
                            Document existingDocument = collection.find(query).first();
                            if (existingDocument != null) {
                                // Identify fields to remove
                                Set<String> existingFields = existingDocument.keySet();
                                Set<String> updatedFields = document.keySet();

                                // Create $unset document
                                Document unsetFields = new Document();
                                for (String field : existingFields) {
                                    if (!updatedFields.contains(field) && !field.equals("_id")) {
                                        unsetFields.append(field, "");
                                    }
                                }

                                // Perform update
                                if (!unsetFields.isEmpty()) {
                                    collection.updateOne(query, new Document("$unset", unsetFields));
                                }

                                // Update existing fields
                                collection.updateOne(query, new Document("$set", document), new UpdateOptions().upsert(true));
                            } else {
                                // Insert new document if not found
                                collection.insertOne(document);
                            }
                        }
                        // Search for a document with the username field
                        String usernameToSearch = "Desirae"; // Replace with the actual username you want to search for
                        Document query = new Document("first_name", usernameToSearch);
                        Document foundDocument = collection.find(query).first();
                        if (foundDocument != null) {
                            System.out.println("Found document: " + foundDocument.toJson());
                        } else {
                            System.out.println("No document found with username: " + usernameToSearch);
                        }
                        System.out.println("--------------");
                        mongoClient.close();
                    }
                    */

                    String[] fields = file.getName().split("\\.");
                    System.out.println("File : " + file);
                    long start = System.currentTimeMillis();
                    JsonNode data = readFile(file);
                    System.out.println(data);
                    long finish = System.currentTimeMillis();
                    long timeElapsed = finish - start;
                    System.out.println("Time to read : " + timeElapsed);

                    // Check this file is no data
                    if (data == null || data.isEmpty()) {
                        // Check back up store
                        if (!Files.exists(binBackUpStore)) {
                            binBackUpStore.toFile().mkdirs();
                        }
                        // Copy file
                        Path binFile = Paths.get(binBackUpStore + "\\" + file.getName());
                        Files.copy(file.toPath(), binFile, StandardCopyOption.REPLACE_EXISTING);
                        //System.out.println(binBackUpStore);
                        Files.delete(file.toPath());
                        continue;
                    }

                    ////////////////  Connect mongodb ////////////////

                    // Connect to MongoDB
                    MongoClient mongoClient = MongoClients.create("mongodb://root:root@localhost:27017/");
                    // Get the database
                    MongoDatabase database = mongoClient.getDatabase("datanosql");
                    // Get the collection
                    MongoCollection<Document> collection = database.getCollection(fields[0]);

                    start = System.currentTimeMillis();
                    for (JsonNode dataRow : data) {
                        //System.out.println(line.toString());
                        // Convert JsonNode to Document
                        Document document = Document.parse(dataRow.toString());

                        // Check miss field
                        if (fields[0].equals("data")) {
                            try {
                                checkFieldsData(document);
                            } catch (Exception e) {
                                //System.out.println("Error : " + e.getMessage());
                            }
                        }

                        ////////////////  Check for duplicate name and update if exists ////////////////

                        // Create query set for fetch data from database
                        Document query = switchQuery(document, fields[0]);

                        // Fetch existing document from the collection
                        Document existingDocument = collection.find(query).first();

                        if (existingDocument != null && !existingDocument.isEmpty()) {
                            if (fields[0].equals("data")) {
                                //System.out.println(existingDocument.get("name"));
                            } else {
                                //System.out.println(existingDocument.get("first_name"));
                            }
                        }

                        if (existingDocument != null) {
                            // Identify fields to remove
                            Set<String> existingFields = existingDocument.keySet();
                            Set<String> updatedFields = document.keySet();

                            // Create $unset document
                            Document unsetFields = new Document();
                            for (String field : existingFields) {
                                if (!updatedFields.contains(field) && !field.equals("_id")) {
                                    unsetFields.append(field, "");
                                }
                            }

                            // Perform update
                            if (!unsetFields.isEmpty()) {
                                collection.updateOne(query, new Document("$unset", unsetFields));
                            }

                            // Update existing fields
                            collection.updateOne(query, new Document("$set", document), new UpdateOptions().upsert(true));
                        } else {
                            // Insert new document if not found
                            collection.insertOne(document);
                        }
                        finish = System.currentTimeMillis();
                        System.out.println("Insert done : " + (finish - start));
                    }
                }
            }
        }

        if (true) {
            return;
        }

        // Loop
        try (DirectoryStream<Path> files = Files.newDirectoryStream(localStore)) {
            for (Path file : files) {
                // Extract the file extension
                String fileType = file.getFileName().toString().substring(file.getFileName().toString().lastIndexOf('.') + 1);
                // Check file type
                if (!checkFileTypeCorrect(fileType)) {
                    Files.delete(file);
                    continue;
                }
                // Do batching
//                try {
//
//                } catch (IOException e) {
//                    System.out.println("File \"" + file.getFileName().toString() + "\" fail to convert be object : Message[" + e.getMessage() + "]");
//                    continue;
//                }

//            // Copy file to back up
//            Path copyFile = Paths.get(subDirectory.toString(),file.getFileName().toString());
//            Files.copy(file,copyFile, StandardCopyOption.REPLACE_EXISTING);
//            // Delete file
//            Files.delete(file);
            }
        }
        System.out.println("Batch successful.");

//        try (DirectoryStream<Path> files = Files.newDirectoryStream(subDirectory)) {
//            for (Path file : files) {
//                System.out.println(file.toString());
//            }
//        }
    }

    private boolean checkFileTypeCorrect(String fileType) {
        for (String allowedFileType : ALLOWED_FILE_TYPES) {
            if (allowedFileType.equalsIgnoreCase(fileType)) {
                return true;
            }
        }
        return false;
    }

    private String generateDirectoryNameByDatetime() {
        LocalDateTime localDateTime = LocalDateTime.now();
        String date = localDateTime.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"));
        String time = localDateTime.format(DateTimeFormatter.ofPattern("HH_mm_ss"));
        return "Date" + date + "Time" + time;
    }

    private String generateDirectoryNameByDate() {
        LocalDate localDate = LocalDate.now();
        String date = localDate.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"));
        return "Date" + date;
    }

//    private void batching(Path file) throws IOException {
//        String fileType = file.getFileName().toString().substring(file.getFileName().toString().lastIndexOf('.') + 1);
//        String fileName = file.getFileName().toString().replace("." + fileType, "");
//        //System.out.println("FileType : " + fileType);
//        //System.out.println("File name : " + fileName);
//        List<Person> persons = new ArrayList<>();
//        switch (fileType) {
//            case "csv":
//                persons = readCsv(file);
//                break;
//            case "json":
//                persons = readJson(file);
//                break;
//            case "txt":
//                persons = readText(file);
//                break;
//            case "xlsx":
//                persons = readXlsx(file);
//                break;
//            case "xml":
//                persons = readXml(file);
//                break;
//        }
//        System.out.println(persons);
//    }
//
//    private List<Person> readXml(Path file) throws IOException {
//        List<Person> persons = new ArrayList<>();
//        ObjectMapper objectMapper = new ObjectMapper();
//        XmlMapper xmlMapper = new XmlMapper();
////        People getPeople = xmlMapper.readValue(file.toFile(), People.class);
////        System.out.println("People  : "+getPeople);
//
//        // Deserialize the XML to a JsonNode
//        JsonNode rootNode = xmlMapper.readTree(file.toFile());
//        // Access the 'person' nodes within the root 'People' node
//        JsonNode personsNode = rootNode.path("person");
//        if (personsNode.isArray()) {
//            for (JsonNode personNode : personsNode) {
//                Person person = objectMapper.readValue(personNode.traverse(), Person.class);
//                persons.add(person);
////                for (JsonNode personNodeChild : personNode){
////                    System.out.println(personNodeChild);
////                }
//            }
//        }
//        return persons;
//    }
//
//    private List<Person> readCsv(Path file) throws IOException {
//        List<Person> persons = new ArrayList<>();
//        try (Reader reader = new FileReader(file.toFile())) {
//            Iterable<CSVRecord> records = CSVFormat.DEFAULT
//                    .withHeader("id", "name", "age", "city")
//                    .withSkipHeaderRecord()
//                    .parse(reader);
//            for (CSVRecord record : records) {
//                String id = record.get("id");
//                String name = record.get("name");
//                int age = Integer.parseInt(record.get("age"));
//                String city = record.get("city");
//                Person person = new Person();
//                person.setId(id);
//                person.setName(name);
//                person.setAge(age);
//                person.setCity(city);
//                persons.add(person);
//            }
//        }
//        return persons;
//    }
//
//    private List<Person> readXlsx(Path file) throws IOException {
//        List<Person> persons = new ArrayList<>();
//        try (FileInputStream fis = new FileInputStream(file.toFile());
//             Workbook workbook = new XSSFWorkbook(fis)) {
//            Sheet sheet = workbook.getSheetAt(0);
//            for (Row row : sheet) {
//                if (row.getRowNum() == 0) continue; // Skip header row
//                String id = Double.toString(row.getCell(0).getNumericCellValue());
//                String name = row.getCell(1).getStringCellValue();
//                int age = (int) row.getCell(2).getNumericCellValue();
//                String city = row.getCell(3).getStringCellValue();
//                Person person = new Person();
//                person.setId(id);
//                person.setName(name);
//                person.setAge(age);
//                person.setCity(city);
//                persons.add(person);
//            }
//        }
//        return persons;
//    }
//
//    private List<Person> readText(Path file) throws IOException {
//        List<Person> persons = new ArrayList<>();
//        try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
//            String line;
//            while ((line = br.readLine()) != null) {
//                String[] parts = line.split(", ");
//                String id = parts[0];
//                String name = parts[1];
//                int age = Integer.parseInt(parts[2]);
//                String city = parts[3];
//                Person person = new Person();
//                person.setId(id);
//                person.setName(name);
//                person.setAge(age);
//                person.setCity(city);
//                persons.add(person);
//            }
//        }
//        return persons;
//    }
//
//    private List<Person> readJson(Path file) throws IOException {
//        ObjectMapper mapper = new ObjectMapper();
//        return mapper.readValue(new File(file.toFile().toString()), new TypeReference<List<Person>>() {
//        });
//    }

    // Clear noise method
    private int clearNonDirectoryAndCountSubDirectory(Path directory) throws IOException {
        File[] files = directory.toFile().listFiles();
        if (files == null || files.length == 0) {
            return 0;
        }
        int subDirectoryCount = 0;
        for (File file : files) {
            if (!file.isDirectory()) {
                Files.delete(file.toPath());
//                System.out.println("Delete file.");
            } else {
                subDirectoryCount++;
            }
        }
        return subDirectoryCount;
    }

    //########## Function clear file and directory ##########

    private int clearNonAndUnAllowedSubDirectory(Path directory, String[] allowDirectoryName) throws IOException {
        File[] files = directory.toFile().listFiles();
        if (files == null) {
            return 0;
        }
        int subDirectoryCount = 0;
        for (File file : files) {
            // Check this file is not directory
            if (!file.isDirectory()) {
                Files.delete(file.toPath());
//                System.out.println("Delete file. : " + file.getName());
            } else {
                File[] subDirectory = file.listFiles();
                // Check directory but null or empty
                if (subDirectory == null || subDirectory.length == 0) {
                    Files.delete(file.toPath());
//                    System.out.println("Sub directory empty have delete. : " + file.getName());
                } else {
                    boolean subDirectoryStatusAllow = false;
                    // Check directory name is allow
                    for (String allowType : allowDirectoryName) {
                        if (allowType.equalsIgnoreCase(file.getName())) {
                            subDirectoryStatusAllow = checkAndClearNonAllowFileInDirectory(file);
                            break;
                        }
                    }
                    if (!subDirectoryStatusAllow) {
//                        System.out.println("Sub directory status is not allow. : " + file.getName());
                        clearDirectory(file);
                        Files.delete(file.toPath());
                    } else {
                        subDirectoryCount++;
                    }
                }
            }
        }
//        System.out.println("Sub directory count : "+subDirectoryCount);
        return subDirectoryCount;
    }

    private void clearDirectory(File directory) throws IOException {
//        System.out.println("This directory have to delete. : " + directory.getName());
        File[] files = directory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                File[] subFiles = file.listFiles();
                if (subFiles != null || subFiles.length != 0) {
                    clearDirectory(file);
                }
            }
            Files.delete(file.toPath());
//            System.out.println("Delete file. : " + file.getName());
        }
    }

    private boolean checkAndClearNonAllowFileInDirectory(File directory) throws IOException {
        String[] allowDirectory = null;
        if (directory.getName().equals("sql")) {
            allowDirectory = ALLOWED_NAME_FILE_SQL;
        } else {
            allowDirectory = ALLOWED_NAME_FILE_NOSQL;
        }
        File[] filesInDirectory = directory.listFiles();
        for (File file : filesInDirectory) {
            boolean thisFileIsAllow = false;
            String[] fileNameOnlyNoType = file.getName().split("\\.");
            for (String allowName : allowDirectory) {
                if (fileNameOnlyNoType[0].equals(allowName)) {
                    thisFileIsAllow = true;
                    break;
                }
            }
            if (!thisFileIsAllow) {
                if (file.isDirectory() && file.listFiles().length != 0) {
                    clearDirectory(file);
                }
                Files.delete(file.toPath());
            }
        }
        return directory.listFiles().length != 0;
    }

    //########## Function read file ##########

    private JsonNode readFile(File file) {
        JsonNode output = null;
        String[] fields = file.getName().split("\\.");
        String fileType = fields[fields.length - 1];
        System.out.println("File Type : " + fileType);
        try {
            switch (fileType) {
                case "txt":
                    output = objectMapper.readTree(file);
                    break;
                case "csv":
                    output = readCsv(file);
                    break;
                case "xlsx":
                    output = readXlsx(file);
                    break;
                case "json":
                    output = objectMapper.readTree(file);
                    break;
                case "xml":
                    XmlMapper xmlMapper = new XmlMapper();
                    JsonNode jsonFromXml = xmlMapper.readTree(file);
                    output = objectMapper.readTree(jsonFromXml.get(fields[0]).toString());
                    break;
            }
        } catch (IOException e) {
            System.out.println("Can't read file.");
        }
        return output;
    }

    private JsonNode readCsv(File file) {
        JsonNode output = null;
        try (FileReader reader = new FileReader(file)) {
            CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(new CSVParserBuilder().withSeparator(',').build())
                    .build();

            // อ่านแถวแรก (header)
            String[] header = csvReader.readNext();

            if (header == null) {
                System.out.println("No data found in CSV file.");
                return output;
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

            output = objectMapper.convertValue(allRows, JsonNode.class);
        } catch (IOException | CsvValidationException e) {
            System.out.println(e);
        }
        return output;
    }

    private JsonNode readXlsx(File file) {
        JsonNode output = null;
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

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
                return output;
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
            output = objectMapper.convertValue(allRows, JsonNode.class);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return output;
    }

    //########## Function check fields ##########

    private void checkFieldsData(Document document) throws StatusException {
        if (document.getInteger("age") == null || document.getInteger("age") == 0 || document.getInteger("age") < 30) {
            throw new StatusException(document + " is low age.");
        }
        if (document.getString("country") == null || document.getString("country").isEmpty()) {
            throw new StatusException(document + " is no filed.");
        }
    }

    private Document switchQuery(Document document, String filename) {
        Document query = null;
        switch (filename) {
            case "data":
                query = new Document("name", document.get("name").toString());
                break;
            case "person":
                query = new Document("first_name", document.get("first_name").toString());
                break;
        }
        return query;
    }

    //########## Function create query ##########

    private class StatusException extends Exception {
        public StatusException(String message) {
            super(message);
        }
    }
}
