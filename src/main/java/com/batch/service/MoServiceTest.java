package com.batch.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

@Service
public class MoServiceTest implements  MoInterface{
    @Override
    public void write(String name) throws IOException{
        // Specify the folder path
        String folderPath = "localstore";
        // Create a File object for the folder
        File folder = new File(folderPath);

        // Create the folder if it does not exist
        if (!folder.exists()) {
            folder.mkdirs(); // Creates the directory and any necessary but nonexistent parent directories
        }

        // Specify the file path within the folder
        File file = new File(folderPath, name);

        // Write to the file
        try (FileWriter fileWriter = new FileWriter(file);
             PrintWriter printWriter = new PrintWriter(fileWriter)) {

            printWriter.print("Test");
            printWriter.printf("""
                Hello world 
                a.Start
                b.Exit
                """);
            printWriter.close();
        }
    }
}
