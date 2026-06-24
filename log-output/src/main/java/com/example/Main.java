package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;

public class Main {

    private static final String RANDOM_STRING = UUID.randomUUID().toString();

    public static void main(String[] args) throws IOException {
        Thread logThread = new Thread(() -> {
            while (true) {
                System.out.println(Instant.now().toString() + ": " + RANDOM_STRING);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        logThread.start();


        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {

            String currentStatus = Instant.now().toString() + ": " + RANDOM_STRING;
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, currentStatus.length());
            exchange.getResponseBody().write(currentStatus.getBytes());
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Server started on port " + port);
    }
}