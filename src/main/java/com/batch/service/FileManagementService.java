package com.batch.service;

import com.batch.dto.response.ResponseGeneral;
import com.batch.dto.response.ResponseWithData;
import com.batch.repository.mongodb.PersonNosqlRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileManagementService {

    private static final String LOCAL_STORE_DIR = "localStore";
    private static final String[] ALLOWED_FILE_TYPES = {"txt", "csv", "xlsx", "json", "xml"};
    private static final String[] ALLOWED_TYPE = {
            "sql",
            "nosql"
    };

    @Autowired
    private PersonNosqlRepository personNosqlRepository;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * File and Path are same function but different packages
     * Path is update in java version 7
     * It delivers an entirely new API to work with I/O
     * In java.nio
     */

    public Object uploadFile(MultipartFile fileUpload, String type) {
        String message;

        // Check if the file is empty
        if (fileUpload.isEmpty()) {
            message = "Please select file to upload.";
            return new ResponseGeneral(Integer.toString(400), message);
        }

        // Check if the type is empty
        if (type.isEmpty()) {
            message = "Please select type.";
            return new ResponseGeneral(Integer.toString(400), message);
        }

        // Check type is correct
        if (!checkType(type)) {
            message = "Type incorrect.";
            return new ResponseGeneral(Integer.toString(400), message);
        }

        // Check if the file type is correct
        if (!checkFileTypeCorrect(fileUpload, ALLOWED_FILE_TYPES)) {
            message = "Support file type only \"txt\",\"csv\",\"xlsx\",\"json\",\"xml\".";
            return new ResponseGeneral(Integer.toString(400), message);
        }


        // Ensure the local directory exists and prepare it
        File dir = checkDirectoryAndPrepare(LOCAL_STORE_DIR);
        System.out.println(dir.toString());
        File subDir = checkDirectoryAndPrepare(dir + "\\" + generateDirectoryNameByDate());
        System.out.println(subDir.toString());
        File typeDir = checkDirectoryAndPrepare(subDir + "\\" + type);
        try {
            // Define destination file
            Path destinationFile = Paths.get(typeDir + "\\" + fileUpload.getOriginalFilename());
            // save file
            fileUpload.transferTo(destinationFile);
            message = "Upload successful";
            return new ResponseGeneral(Integer.toString(200), message);
        } catch (IOException e) {
            //e.printStackTrace();
            message = "File upload" + e;
            return new ResponseGeneral(Integer.toString(400), message);
        }
    }

    public Object getAllFilesFromDirectory() {
        List<FileRespond> fileLists = new ArrayList<>();
        String message;
        try {
            // get directory local store
            DirectoryStream<Path> directoryLocalStore = Files.newDirectoryStream(Paths.get(LOCAL_STORE_DIR));
            for (Path path : directoryLocalStore) {
                // check path is not directory
                if (!Files.isDirectory(path)) {
                    String dateCreate = convertCreateDateFromPathToString(path);
                    fileLists.add(new FileRespond(path.getFileName().toString(), dateCreate));
                }

                deleteFileByCheckEqualsBetweenPathAndFilename(path, "New Microsoft Excel Worksheet.xlsx");
            }
            message = "Get all files successful.";
            return new ResponseWithData(Integer.toString(200), message, fileLists);
        } catch (IOException e) {
            e.printStackTrace();
            message = "File get file lists" + e;
            return new ResponseGeneral(Integer.toString(400), message);
        }
    }

    // ### private method ###

    private File checkDirectoryAndPrepare(String dirName) {
        File dir = new File(dirName);
        // ensure directory exists
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private boolean checkFileTypeCorrect(MultipartFile fileUpload, String[] allowedFileTypes) {
        // Ensure the filename is not null and contains at least one period
        String originalFilename = fileUpload.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return false;
        }

        // Extract the file extension
        String fileType = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);

        // Check if the file extension is in the list of allowed file types
        for (String allowedFileType : allowedFileTypes) {
            if (allowedFileType.equalsIgnoreCase(fileType)) {
                return true;
            }
        }

        // Return false if the file type is not allowed
        return false;
    }

    private String convertCreateDateFromPathToString(Path path) throws IOException {
        // convert path to file attributes
        BasicFileAttributes fileAttributes = Files.readAttributes(path, BasicFileAttributes.class);
        // convert attributes date time to local date time
        LocalDateTime localDateTime = fileAttributes.creationTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return localDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    private boolean deleteFileByCheckEqualsBetweenPathAndFilename(Path path, String filename) throws IOException {
        if (path.getFileName().toString().equals(filename)) {
            Files.delete(path);
            return true;
        } else {
            return false;
        }
    }

    private String generateDirectoryNameByDate() {
        LocalDate localDate = LocalDate.now();
        String date = localDate.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"));
        return "Date" + date;
    }

    private boolean checkType(String type) {
        for (String allowType : ALLOWED_TYPE) {
            if (allowType.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    private String getFileType(MultipartFile fileUpload) {
        return fileUpload.getOriginalFilename().substring(fileUpload.getOriginalFilename().lastIndexOf('.') + 1);
    }


    // ### private class ###
    @Data
    private class FileRespond {
        private String fileName;
        private String createDate;

        public FileRespond() {
        }

        public FileRespond(String fileName, String createDate) {
            this.fileName = fileName;
            this.createDate = createDate;
        }
    }
}
