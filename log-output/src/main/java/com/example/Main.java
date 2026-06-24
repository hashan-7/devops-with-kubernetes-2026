package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

public class Main {
    private static final String RANDOM_STRING = UUID.randomUUID().toString();
    private static final String FILE_PATH = "/usr/src/app/files/count.txt";

    public static void main(String[] args) throws IOException {
        Thread logThread = new Thread(() -> {
            while (true) {
                String count = "0";
                try {
                    if (Files.exists(Paths.get(FILE_PATH))) {
                        count = Files.readString(Paths.get(FILE_PATH)).trim();
                    }
                } catch (IOException e) {
                    // ignore
                }
                System.out.println(Instant.now().toString() + ": " + RANDOM_STRING + ". Ping / Pongs: " + count);
                try { Thread.sleep(5000); } catch (InterruptedException e) { break; }
            }
        });
        logThread.start();

        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            String count = "0";
            try {
                if (Files.exists(Paths.get(FILE_PATH))) {
                    count = Files.readString(Paths.get(FILE_PATH)).trim();
                }
            } catch (IOException e) {
                count = "0";
            }


            String response = Instant.now().toString() + ": " + RANDOM_STRING + ". Ping / Pongs: " + count;
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Log-output server started on port " + port);
    }
}