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
    @RequestMapping("/upload")
    public Object upload(@RequestParam("file") MultipartFile file){
        return fileManagementService.uploadFile(file);
    }

    @GetMapping
    @RequestMapping("/getFileLists")
    public Object getFileList(){
        return fileManagementService.getAllFilesFromDirectory();
    }

    @GetMapping
    @RequestMapping("/test/add")
    public void test(@RequestBody String req) throws JsonProcessingException {
        fileManagementService.testAddUser(req);
    }

    @GetMapping
    @RequestMapping("/test")
    public Object testGetAll(){
       return fileManagementService.getUser();
    }

    @GetMapping
    @RequestMapping("/test/get/{name}")
    public Object testGetByName(@PathVariable String name){
        return fileManagementService.getByName(name);
    }

    @Data
    private class FileInput {
        private String originalFilename;
        private String contenType;
        private Long size;
    }
}
