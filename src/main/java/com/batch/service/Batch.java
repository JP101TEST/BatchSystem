package com.batch.service;

import com.batch.entity.file.xml.Person;
import com.batch.repository.mongodb.PersonNosqlRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class Batch {
    private static final String LOCAL_STORE_DIR = "localStore";
    private static final String BACKUP_STORE_DIR = "backupStore";
    private static final String[] ALLOWED_FILE_TYPES = {"txt", "csv", "xlsx", "json", "xml"};
    private static final String[] ALLOWED_NAME_FILE_SQL = {""};
    private static final String[] ALLOWED_NAME_FILE_NOSQL = {""};
    private static final String[] ALLOWED_FILE = {"person", "school"};
    private static final String[] ALLOWED_TYPE = {
            "sql",
            "nosql"
    };
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PersonNosqlRepository personNosqlRepository;

    public Batch(PersonNosqlRepository personNosqlRepository) {
        this.personNosqlRepository = personNosqlRepository;
    }


    public void batch() throws IOException, CsvValidationException {
        // Get directory
        Path localStore = Paths.get(LOCAL_STORE_DIR);
        Path backUpStore = Paths.get(BACKUP_STORE_DIR);
        Path subLocalDirectory = Paths.get(localStore + "\\" + generateDirectoryNameByDate());

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

        // Clear non format directory in local  Datedd_mm_yyyy

        // Check sub local empty
        if (!Files.exists(subLocalDirectory)) {
            System.out.println("Sub local store for insert not found.");
            return;
        }

        // Check sub local empty and clear non and non-allowed directory sql nosql
        if (clearNonAndUnAllowedSubDirectory(subLocalDirectory, ALLOWED_TYPE) == 0) {
            System.out.println("Sub local store for insert not found.");
            return;
        }

        //////////////////////////////////////////////

        // Check back up store
        if (!Files.exists(backUpStore)) {
            backUpStore.toFile().mkdirs();
        }
        // Create subdirectory.
        Path subDirectory = Paths.get(BACKUP_STORE_DIR, subLocalDirectory.toFile().getName());
        // Create subdirectory back up.
        subDirectory.toFile().mkdirs();

        File[] directory = subLocalDirectory.toFile().listFiles();
        for (File dir : directory) {
            File[] files = dir.listFiles();
            if (files != null || files.length != 0) {
                for (File file : files) {
                    String[] fields = file.getName().split("\\.");
                    if (fields[0].equalsIgnoreCase("person")) {
                        String fileType = file.getName().toString().substring(file.getName().toString().lastIndexOf('.') + 1);
                        JsonNode people = null;
                        switch (fileType) {
                            case "json":
                                people = objectMapper.readTree(file);
                                break;
                            case "xml":
                                XmlMapper xmlMapper = new XmlMapper();
                                JsonNode xmlPeople = xmlMapper.readTree(file);
//                                System.out.println(objectMapper.readTree(xmlPeople.toString()));
                                for (JsonNode xmlPerson : xmlPeople.get("person")) {
                                    System.out.println(xmlPerson);
                                }
                                System.out.println("------  xml  ------");
                                break;
                            case "txt":
                                JsonNode txtPeople = objectMapper.readTree(file);
//                                System.out.println(Files.readString(file.toPath()));
                                for (JsonNode person : txtPeople) {
                                    System.out.println(person);
                                }
                                System.out.println("------  txt  ------");
                                break;
                            case "csv": // แก้
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

                                    allRows.forEach(sd->{
                                        System.out.println(sd);
                                    });
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

                                    allRows.forEach(sd->{
                                        System.out.println(sd);
                                    });
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                System.out.println("------  xlsx  ------");
                                break;
                        }

                        if (people == null) {
                            continue;
                        }

                        System.out.println(people);

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
                            if (document.get("age") != null) {
                                document.remove("age");
                            }

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
                try {
                    batching(file);
                } catch (IOException e) {
                    System.out.println("File \"" + file.getFileName().toString() + "\" fail to convert be object : Message[" + e.getMessage() + "]");
                    continue;
                }

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

    private boolean checkFileInDirectory(Path directory) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                return true;
            }
            return false;
        }
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

    private void batching(Path file) throws IOException {
        String fileType = file.getFileName().toString().substring(file.getFileName().toString().lastIndexOf('.') + 1);
        String fileName = file.getFileName().toString().replace("." + fileType, "");
        //System.out.println("FileType : " + fileType);
        //System.out.println("File name : " + fileName);
        List<Person> persons = new ArrayList<>();
        switch (fileType) {
            case "csv":
                persons = readCsv(file);
                break;
            case "json":
                persons = readJson(file);
                break;
            case "txt":
                persons = readText(file);
                break;
            case "xlsx":
                persons = readXlsx(file);
                break;
            case "xml":
                persons = readXml(file);
                break;
        }
        System.out.println(persons);
    }

    private List<Person> readXml(Path file) throws IOException {
        List<Person> persons = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        XmlMapper xmlMapper = new XmlMapper();
//        People getPeople = xmlMapper.readValue(file.toFile(), People.class);
//        System.out.println("People  : "+getPeople);

        // Deserialize the XML to a JsonNode
        JsonNode rootNode = xmlMapper.readTree(file.toFile());
        // Access the 'person' nodes within the root 'People' node
        JsonNode personsNode = rootNode.path("person");
        if (personsNode.isArray()) {
            for (JsonNode personNode : personsNode) {
                Person person = objectMapper.readValue(personNode.traverse(), Person.class);
                persons.add(person);
//                for (JsonNode personNodeChild : personNode){
//                    System.out.println(personNodeChild);
//                }
            }
        }
        return persons;
    }

    private List<Person> readCsv(Path file) throws IOException {
        List<Person> persons = new ArrayList<>();
        try (Reader reader = new FileReader(file.toFile())) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withHeader("id", "name", "age", "city")
                    .withSkipHeaderRecord()
                    .parse(reader);
            for (CSVRecord record : records) {
                String id = record.get("id");
                String name = record.get("name");
                int age = Integer.parseInt(record.get("age"));
                String city = record.get("city");
                Person person = new Person();
                person.setId(id);
                person.setName(name);
                person.setAge(age);
                person.setCity(city);
                persons.add(person);
            }
        }
        return persons;
    }

    private List<Person> readXlsx(Path file) throws IOException {
        List<Person> persons = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header row
                String id = Double.toString(row.getCell(0).getNumericCellValue());
                String name = row.getCell(1).getStringCellValue();
                int age = (int) row.getCell(2).getNumericCellValue();
                String city = row.getCell(3).getStringCellValue();
                Person person = new Person();
                person.setId(id);
                person.setName(name);
                person.setAge(age);
                person.setCity(city);
                persons.add(person);
            }
        }
        return persons;
    }

    private List<Person> readText(Path file) throws IOException {
        List<Person> persons = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(", ");
                String id = parts[0];
                String name = parts[1];
                int age = Integer.parseInt(parts[2]);
                String city = parts[3];
                Person person = new Person();
                person.setId(id);
                person.setName(name);
                person.setAge(age);
                person.setCity(city);
                persons.add(person);
            }
        }
        return persons;
    }

    private List<Person> readJson(Path file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new File(file.toFile().toString()), new TypeReference<List<Person>>() {
        });
    }

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
                    for (String allowType : allowDirectoryName) {
                        if (allowType.equalsIgnoreCase(file.getName())) {
                            subDirectoryStatusAllow = true;
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

    private static Object parseValue(String key, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (key.endsWith("hobbies")) {
            // แปลงเป็น array
            return value.replace("[", "").replace("]", "").replace("\"", "").split(", ");
        } else {
            // แปลงเป็นชนิดข้อมูลปกติ
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return value;
            }
        }
    }

    private static void putValue(Map<String, Object> map, String key, Object value) {
        String[] parts = key.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            current.putIfAbsent(part, new HashMap<String, Object>());
            if (current.get(part) instanceof Map) {
                current = (Map<String, Object>) current.get(part);
            }
        }

        current.put(parts[parts.length - 1], value);
    }
}
