package com.example;

import java.time.Instant;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        String randomString = UUID.randomUUID().toString();

        while (true) {
            String timestamp = Instant.now().toString();
            System.out.println(timestamp + ": " + randomString);

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
}