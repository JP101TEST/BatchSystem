package com.batch.service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Batch {
    private static final String LOCAL_STORE_DIR = "localStore";
    private static final String BACKUP_STORE_DIR = "backupStore";
    private static final String[] ALLOWED_FILE_TYPES = {"txt", "csv", "xlsx", "json", "xml"};

    public void batch() throws IOException {
        // Get directory
        Path localStore = Paths.get(LOCAL_STORE_DIR);
        Path backUpStore = Paths.get(BACKUP_STORE_DIR);
        // Check local store
        if (!Files.exists(localStore)) {
            System.out.println("Local store not found.");
            return;
        }
        // Check file in local store
        if (!checkFileInDirectory(localStore)){
            System.out.println("No file.");
            return;
        }
        // Check back up store
        if (!Files.exists(backUpStore)) {
            backUpStore.toFile().mkdirs();
        }
        // Create subdirectory.
        Path subDirectory = Paths.get(BACKUP_STORE_DIR,generateDirectoryNameByDatetime());
        // Create subdirectory back up.
        subDirectory.toFile().mkdirs();
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
            batching(file);
            // Copy file to back up
            Path copyFile = Paths.get(subDirectory.toString(),file.getFileName().toString());
            Files.copy(file,copyFile, StandardCopyOption.REPLACE_EXISTING);
            // Delete file
            Files.delete(file);
            }
        }

        try (DirectoryStream<Path> files = Files.newDirectoryStream(subDirectory)) {
            for (Path file : files) {
                System.out.println(file.toString());
            }
        }
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


    private String generateDirectoryNameByDatetime(){
        LocalDateTime localDateTime = LocalDateTime.now();
        String date = localDateTime.format(DateTimeFormatter.ofPattern("dd_MM_yyyy"));
        String time = localDateTime.format(DateTimeFormatter.ofPattern("HH_mm_ss"));
        return  "Date" + date + "Time" + time;
    }

    private void batching(Path file){

    }
}
