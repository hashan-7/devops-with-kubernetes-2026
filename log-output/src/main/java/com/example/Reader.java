package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Reader {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        String filePath = "/usr/src/app/files/log.txt";

        server.createContext("/", exchange -> {
            String response = "";
            try {
                if (Files.exists(Paths.get(filePath))) {
                    List<String> lines = Files.readAllLines(Paths.get(filePath));
                    if (!lines.isEmpty()) {
                        response = lines.get(lines.size() - 1);
                    }
                } else {
                    response = "Log file not found yet.";
                }
            } catch (IOException e) {
                response = "Error reading log file.";
            }

            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Reader server started on port " + port);
    }
}