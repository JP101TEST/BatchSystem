package com.batch.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
public class FileManagement {

    private static final String UPLOAD_DIR = "localstore";

    public Object uploadFile(MultipartFile fileUpload) {
        if (fileUpload.isEmpty()) {
            return "Please elect a file to upload.";
        }
        try {
            // ensure directory exists
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) {
                boolean dirsCreated = dir.mkdirs(); // Create directory if it does not exist
                if (!dirsCreated) {
                    throw new IOException("Failed to create directory " + UPLOAD_DIR);
                }
            }
            // define destination file
            File destinationFile = new File(dir, fileUpload.getOriginalFilename());
            // save file
            fileUpload.transferTo(new File(destinationFile.getAbsolutePath()));
            return "Upload file successfully";
        } catch (IOException e) {
            e.printStackTrace();
            return "File upload failed.";
        }
    }
}
