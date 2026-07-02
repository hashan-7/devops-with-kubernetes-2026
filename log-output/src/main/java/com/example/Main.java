package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    private static final String RANDOM_STRING = UUID.randomUUID().toString();

    public static void main(String[] args) throws IOException {
        Thread logThread = new Thread(() -> {
            while (true) {
                String count = fetchPongCount();
                String fileContent = "";
                try {
                    fileContent = Files.readString(Paths.get("/config/information.txt")).trim();
                } catch (IOException e) {
                    fileContent = "Error reading file";
                }
                String messageEnv = System.getenv("MESSAGE");

                System.out.println("file content: " + fileContent);
                System.out.println("env variable: MESSAGE=" + messageEnv);
                System.out.println(Instant.now().toString() + ": " + RANDOM_STRING + ". Ping / Pongs: " + count);

                try { Thread.sleep(5000); } catch (InterruptedException e) { break; }
            }
        });
        logThread.start();

        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            String count = fetchPongCount();
            String fileContent = "";
            try {
                fileContent = Files.readString(Paths.get("/config/information.txt")).trim();
            } catch (IOException e) {
                fileContent = "Error reading file";
            }
            String messageEnv = System.getenv("MESSAGE");

            String response = "file content: " + fileContent + "\n" +
                    "env variable: MESSAGE=" + messageEnv + "\n" +
                    Instant.now().toString() + ": " + RANDOM_STRING + ". Ping / Pongs: " + count;

            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });


        server.createContext("/healthz", exchange -> {
            if (isPingPongReady()) {
                String response = "OK";
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
            } else {
                String response = "Error: Ping-pong unreachable";
                exchange.sendResponseHeaders(500, response.length());
                exchange.getResponseBody().write(response.getBytes());
            }
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Log-output server started on port " + port);
    }

    private static String fetchPongCount() {
        try {

            URL url = new URL("http://ping-pong-svc:80/pongs");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            try (InputStream in = conn.getInputStream()) {
                return new String(in.readAllBytes()).trim();
            }
        } catch (Exception e) {
            return "0";
        }
    }

    private static boolean isPingPongReady() {
        try {
            URL url = new URL("http://ping-pong-svc:80/pongs");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}