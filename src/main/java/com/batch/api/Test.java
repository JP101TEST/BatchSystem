package com.batch.api;

import com.batch.service.FileManagement;
import com.batch.service.MoServiceTest;
import lombok.Data;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
public class Test {

    private final MoServiceTest moService;
    private final FileManagement fileManagement;

    public Test(MoServiceTest moService,FileManagement fileManagement) {
        this.moService = moService;
        this.fileManagement = fileManagement;
    }

    @GetMapping
    @RequestMapping("/")
    public String helloWorld() {
        return "Hello World";
    }

    @GetMapping
    @RequestMapping("/s")
    public String printS() {
        return "S";
    }

    @GetMapping
    @RequestMapping("/tes")
    public String printTes() {
        return "Tes";
    }

    @GetMapping
    @RequestMapping("/tt")
    public String printTt() {
        return "Tt";
    }

    @PostMapping
    @RequestMapping("/plus")
    public String plus(@RequestParam int x, @RequestParam int y) {
        return x + " + " + y + " = " + (x + y);
    }

    @GetMapping
    @RequestMapping("/write")
    public void writeTest(@RequestParam String name) throws IOException {
        moService.write(name);
    }

    @PostMapping
    @RequestMapping("/upload")
    public Object upload(@RequestParam("file") MultipartFile file){
        return fileManagement.uploadFile(file);
    }

    @Data
    private class FileInput {
        private String originalFilename;
        private String contenType;
        private Long size;
    }
}
