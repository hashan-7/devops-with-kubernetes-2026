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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final String DIRECTORY_PATH = "/usr/src/app/files";
    private static final String FILE_PATH = DIRECTORY_PATH + "/image.jpg";
    private static boolean isDownloading = false;
    private static String backendUrl;
    private static boolean isHealthy = true;

    private static class Todo {
        int id;
        String content;
        boolean done;
        Todo(int id, String content, boolean done) {
            this.id = id;
            this.content = content;
            this.done = done;
        }
    }

    public static void main(String[] args) throws IOException {
        File dir = new File(DIRECTORY_PATH);
        if (!dir.exists()) dir.mkdirs();

        ensureImageExists();

        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        backendUrl = System.getenv("BACKEND_URL");
        if (backendUrl == null) {
            backendUrl = "http://todo-backend-svc.project:2345/todos";
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/")) {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    checkAndTriggerDownload();

                    List<Todo> todos = fetchTodosFromBackend();

                    StringBuilder listHtml = new StringBuilder("<ul style=\"list-style-type: none; padding: 0;\">");
                    for (Todo todo : todos) {
                        listHtml.append("<li style=\"margin-bottom: 10px; padding: 10px; border: 1px solid #ccc; width: 400px; display: flex; justify-content: space-between; align-items: center; border-radius: 5px; background: #f9f9f9;\">");
                        if (todo.id == -1) {
                            listHtml.append("<span>").append(todo.content).append("</span>");
                        } else if (todo.done) {
                            listHtml.append("<span style=\"color: #155724; font-weight: bold;\">✓ Done: <strike style=\"color: #6c757d; font-weight: normal;\">").append(todo.content).append("</strike></span>");
                        } else {
                            listHtml.append("<span>").append(todo.content).append("</span>");
                            listHtml.append("<button onclick=\"markDone(").append(todo.id).append(")\" style=\"background: #0d6efd; color: white; border: none; padding: 5px 10px; cursor: pointer; border-radius: 3px;\">Mark done</button>");
                        }
                        listHtml.append("</li>");
                    }
                    listHtml.append("</ul>");

                    String response = "<html><head><title>Todo App</title>" +
                            "<script>" +
                            "   async function markDone(id) {" +
                            "       try {" +
                            "           await fetch('/todos/' + id, { method: 'PUT' });" +
                            "           window.location.reload();" +
                            "       } catch (error) {" +
                            "           console.error('Error updating todo:', error);" +
                            "       }" +
                            "   }" +
                            "</script>" +
                            "</head><body style=\"font-family: Arial, sans-serif; padding: 20px;\">" +
                            "<h1>Todo App</h1>" +
                            "<img src=\"/image\" style=\"max-width:100%; max-height:400px; border-radius: 10px;\" /><br/><br/>" +
                            "<form action=\"/todos\" method=\"POST\" style=\"margin-bottom: 20px;\">" +
                            "   <input type=\"text\" name=\"content\" maxlength=\"140\" placeholder=\"Enter your todo...\" style=\"width: 300px; padding: 8px; border: 1px solid #ccc; border-radius: 4px;\" required /> " +
                            "   <button type=\"submit\" style=\"padding: 8px 15px; background: #198754; color: white; border: none; border-radius: 4px; cursor: pointer;\">Send</button>" +
                            "</form>" +
                            "<h3>My Todos:</h3>" +
                            listHtml.toString() +
                            "<br/><br/>" +
                            "<button id=\"break-btn\" style=\"background: #dc3545; color: white; padding: 10px 15px; cursor: pointer; border: none; border-radius: 5px; font-weight: bold;\">" +
                            "   Break the app" +
                            "</button>" +
                            "<script>" +
                            "   document.getElementById('break-btn').addEventListener('click', async () => {" +
                            "       try {" +
                            "           await fetch('/healthz', { method: 'POST' });" +
                            "           alert('App is broken! Kubernetes will notice the failed Liveness Probe and restart the pod soon.');" +
                            "       } catch (error) {" +
                            "           console.error('Error breaking app:', error);" +
                            "       }" +
                            "   });" +
                            "</script>" +
                            "</body></html>";

                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } else if (path.equals("/image") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                File file = new File(FILE_PATH);
                if (file.exists()) {
                    exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
                    exchange.sendResponseHeaders(200, file.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        Files.copy(file.toPath(), os);
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.getResponseBody().close();
                }
            } else if (path.equals("/todos") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes());
                sendTodoToBackend(body);

                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, -1);
                exchange.getResponseBody().close();
            } else if (path.startsWith("/todos/") && "PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                String idStr = path.substring("/todos/".length());
                try {
                    URL url = new URL(backendUrl + "/" + idStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PUT");
                    int code = conn.getResponseCode();
                    exchange.sendResponseHeaders(code, -1);
                } catch (Exception e) {
                    exchange.sendResponseHeaders(500, -1);
                }
                exchange.getResponseBody().close();
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.getResponseBody().close();
            }
        });

        server.createContext("/healthz", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                isHealthy = false;
                String response = "Frontend is now broken!";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            if (!isHealthy) {
                String response = "Error: App is broken (Unhealthy)";
                exchange.sendResponseHeaders(500, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
                return;
            }

            String response = "OK";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Frontend started on port " + port);
    }

    private static List<Todo> fetchTodosFromBackend() {
        List<Todo> list = new ArrayList<>();
        try {
            URL url = new URL(backendUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            try (InputStream in = conn.getInputStream()) {
                String raw = new String(in.readAllBytes()).trim();


                Pattern p = Pattern.compile("\\{\"id\":(\\d+),\"content\":\"(.*?)\",\"done\":(true|false)\\}");
                Matcher m = p.matcher(raw);
                while(m.find()) {
                    list.add(new Todo(
                            Integer.parseInt(m.group(1)),
                            m.group(2),
                            Boolean.parseBoolean(m.group(3))
                    ));
                }
            }
        } catch (Exception e) {
            list.add(new Todo(-1, "Backend unavailable: " + e.getMessage(), false));
        }
        return list;
    }

    private static void sendTodoToBackend(String body) {
        try {
            URL url = new URL(backendUrl);
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