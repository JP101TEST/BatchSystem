package com.batch.api;

import com.batch.service.DownloadService;
import com.batch.service.FileManagementService;
import com.batch.service.SearchService;
import com.batch.service.UploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

@RestController
public class FileManagementController {

    private final FileManagementService fileManagementService;
    private final UploadService uploadService;
    private  final SearchService searchService;
    private final DownloadService downloadService;
    public FileManagementController(FileManagementService fileManagementService, UploadService uploadService, SearchService searchService, DownloadService downloadService) {
        this.fileManagementService = fileManagementService;
        this.uploadService = uploadService;
        this.searchService = searchService;
        this.downloadService = downloadService;
    }

    @GetMapping
    @RequestMapping("/")
    public String helloWorld() {
        return "Hello World";
    }

    @PostMapping
    @RequestMapping("/upload")
    public Object uploadFrom(
            @RequestParam("file") MultipartFile file,
            @RequestParam("database_type") String databaseType
    ) {

        return uploadService.upload(file, databaseType);
    }

    @GetMapping
    @RequestMapping("/readAll")
    public Object readAll() {
        return searchService.readAll();
    }

    @CrossOrigin(origins = "http://127.0.0.1:5500/")
    @PostMapping
    @RequestMapping("/search")
    public Object search(
            @RequestBody String req
    ) {
        return searchService.search(req);
    }
    @CrossOrigin(origins = "http://127.0.0.1:5500/")
    @PostMapping
    @RequestMapping("/download")
    public Object download(@RequestBody String req){
        System.out.println(req);
       return downloadService.download(req);
    }

    @PostMapping
    @RequestMapping("/upload/{type}")
    public Object upload(@RequestParam("file") MultipartFile file, @PathVariable String type) {
        return fileManagementService.uploadFile(file, type);
    }

    @GetMapping
    @RequestMapping("/getFileLists")
    public Object getFileList() {
        return fileManagementService.getAllFilesFromDirectory();
    }

}
