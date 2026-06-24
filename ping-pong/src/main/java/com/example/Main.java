package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final String FILE_PATH = "/usr/src/app/files/count.txt";
    private static final AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws IOException {
        try {
            if (Files.exists(Paths.get(FILE_PATH))) {
                String content = Files.readString(Paths.get(FILE_PATH)).trim();
                if (!content.isEmpty()) {
                    counter.set(Integer.parseInt(content));
                }
            }
        } catch (Exception e) {
            System.out.println("No previous count found, starting from 0");
        }

        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/pingpong", exchange -> {
            int currentCount = counter.getAndIncrement();


            try (FileWriter writer = new FileWriter(FILE_PATH)) {
                writer.write(String.valueOf(counter.get()));
            } catch (IOException e) {
                e.printStackTrace();
            }

            String response = "pong " + currentCount;
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Ping-pong server started on port " + port);
    }
}