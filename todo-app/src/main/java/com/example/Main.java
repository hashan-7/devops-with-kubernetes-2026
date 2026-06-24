package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class Main {
    private static final String DIRECTORY_PATH = "/usr/src/app/files";
    private static final String FILE_PATH = DIRECTORY_PATH + "/image.jpg";
    private static boolean isDownloading = false;

    public static void main(String[] args) throws IOException {
        File dir = new File(DIRECTORY_PATH);
        if (!dir.exists()) dir.mkdirs();

        ensureImageExists();

        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            if (!exchange.getRequestURI().getPath().equals("/")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            checkAndTriggerDownload();

            String response = "<html><body><h1>Todo App</h1><img src=\"/image\" style=\"max-width:100%; max-height:400px;\" /></body></html>";
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        server.createContext("/image", exchange -> {
            File file = new File(FILE_PATH);
            if (!file.exists()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Server started on port " + port);
    }

    private static void ensureImageExists() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            downloadImage();
        }
    }

    private static synchronized void checkAndTriggerDownload() {
        File file = new File(FILE_PATH);

        if (file.exists() && (System.currentTimeMillis() - file.lastModified() > 10 * 60 * 1000)) {
            if (!isDownloading) {
                isDownloading = true;
                new Thread(() -> {
                    downloadImage();
                    synchronized (Main.class) {
                        isDownloading = false;
                    }
                }).start();
            }
        }
    }

    private static void downloadImage() {
        try {
            URL url = new URL("https://picsum.photos/1200");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, new File(FILE_PATH).toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Downloaded new image successfully.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}