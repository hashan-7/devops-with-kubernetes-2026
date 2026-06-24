package com.example;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.UUID;

public class Writer {
    private static final String RANDOM_STRING = UUID.randomUUID().toString();

    public static void main(String[] args) throws Exception {
        String filePath = "/usr/src/app/files/log.txt";
        while (true) {
            try (PrintWriter out = new PrintWriter(new FileWriter(filePath, true))) {
                String logLine = Instant.now().toString() + ": " + RANDOM_STRING;
                out.println(logLine);
                System.out.println("Wrote: " + logLine);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Thread.sleep(5000);
        }
    }
}