package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final List<String> todos = new ArrayList<>();
    static {
        todos.add("Learn Kubernetes Basics");
        todos.add("Configure Persistent Volumes");
        todos.add("Complete Chapter 2 Exercises");
    }

    public static void main(String[] args) throws IOException {
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/todos", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < todos.size(); i++) {
                    sb.append("\"").append(todos.get(i)).append("\"");
                    if (i < todos.size() - 1) sb.append(",");
                }
                sb.append("]");
                String response = sb.toString();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes()).trim();
                String todoText = body;
                if (body.contains("content=")) {
                    todoText = java.net.URLDecoder.decode(body.split("content=")[1], "UTF-8");
                }
                if (!todoText.isEmpty()) {
                    todos.add(todoText);
                    System.out.println("Added new todo: " + todoText);
                }
                exchange.sendResponseHeaders(200, 0);
            }
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Backend server started on port " + port);
    }
}