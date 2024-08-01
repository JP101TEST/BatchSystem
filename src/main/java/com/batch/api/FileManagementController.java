package com.batch.api;

import com.batch.service.FileManagementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileManagementController {

    private final FileManagementService fileManagementService;

    public FileManagementController(FileManagementService fileManagementService) {
        this.fileManagementService = fileManagementService;
    }

    @GetMapping
    @RequestMapping("/")
    public String helloWorld() {
        return "Hello World";
    }

    @PostMapping
    @RequestMapping("/upload/{type}")
    public Object upload(@RequestParam("file") MultipartFile file,@PathVariable String type){
        return fileManagementService.uploadFile(file,type);
    }

    @GetMapping
    @RequestMapping("/getFileLists")
    public Object getFileList(){
        return fileManagementService.getAllFilesFromDirectory();
    }

    @Data
    private class FileInput {
        private String originalFilename;
        private String contenType;
        private Long size;
    }
}
