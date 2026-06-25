package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

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
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/")) {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    checkAndTriggerDownload();


                    List<String> todos = fetchTodosFromBackend();

                    StringBuilder listHtml = new StringBuilder("<ul>");
                    for (String todo : todos) {
                        listHtml.append("<li>").append(todo).append("</li>");
                    }
                    listHtml.append("</ul>");

                    String response = "<html><body>" +
                            "<h1>Todo App</h1>" +
                            "<img src=\"/image\" style=\"max-width:100%; max-height:400px;\" /><br/><br/>" +

                            "<form action=\"/todos\" method=\"POST\" style=\"margin-bottom: 20px;\">" +
                            "   <input type=\"text\" name=\"content\" maxlength=\"140\" placeholder=\"Enter your todo...\" style=\"width: 300px; padding: 5px Ramsay;\" required /> " +
                            "   <button type=\"submit\">Send</button>" +
                            "</form>" +
                            "<h3>My Todos:</h3>" +
                            listHtml.toString() +
                            "</body></html>";

                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } else if (path.equals("/todos") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {

                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes());

                sendTodoToBackend(body);


                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, -1);
                exchange.getResponseBody().close();
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.getResponseBody().close();
            }
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
        System.out.println("Frontend started on port " + port);
    }

    private static List<String> fetchTodosFromBackend() {
        List<String> list = new ArrayList<>();
        try {
            URL url = new URL("http://todo-backend-svc:2345/todos");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            try (InputStream in = conn.getInputStream()) {
                String raw = new String(in.readAllBytes()).trim();
                if (raw.length() > 2) {
                    String clean = raw.substring(1, raw.length() - 1);
                    String[] items = clean.split("\",\"");
                    for (String item : items) {
                        list.add(item.replace("\"", ""));
                    }
                }
            }
        } catch (Exception e) {
            list.add("Backend unavailable: " + e.getMessage());
        }
        return list;
    }

    private static void sendTodoToBackend(String body) {
        try {
            URL url = new URL("http://todo-backend-svc:2345/todos");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
            }
            conn.getResponseCode();
        } catch (Exception e) {
            e.printStackTrace();
        }
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