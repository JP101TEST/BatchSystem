package com.batch.api;

import com.batch.service.DownloadService;
import com.batch.service.SearchService;
import com.batch.service.UploadService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileManagementController {

    private final UploadService uploadService;
    private  final SearchService searchService;
    private final DownloadService downloadService;
    public FileManagementController(UploadService uploadService, SearchService searchService, DownloadService downloadService) {
        this.uploadService = uploadService;
        this.searchService = searchService;
        this.downloadService = downloadService;
    }

    @CrossOrigin(origins = "http://127.0.0.1:5500/")
    @PostMapping
    @RequestMapping("/upload")
    public Object uploadFrom(
            @RequestParam("file") MultipartFile file,
            @RequestParam("database_type") String databaseType,
            @RequestParam("entity_type") String entityType
    ) {

        return uploadService.upload(file, databaseType,entityType);
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
       return downloadService.download(req);
    }

}
