package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String DB_URL = "jdbc:postgresql://todo-postgres-svc.project:5432/todos";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = System.getenv("DB_PASSWORD");

    public static void main(String[] args) throws IOException {
        ensureTableExists();

        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/todos", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                List<String> todos = fetchTodos();
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

                System.out.println("Received todo POST request. Raw content: " + body);

                String todoText = body;
                if (body.contains("content=")) {
                    String[] parts = body.split("content=");
                    if (parts.length > 1) {
                        todoText = java.net.URLDecoder.decode(parts[1], "UTF-8");
                    } else {
                        todoText = "";
                    }
                }


                if (todoText.length() > 140) {
                    String errorLog = "Validation failed: Todo length exceeds 140 characters. Length: " + todoText.length();
                    System.err.println(errorLog);

                    String response = "Rejected: Todo cannot exceed 140 characters.";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    exchange.getResponseBody().write(response.getBytes());
                } else if (!todoText.isEmpty()) {
                    saveTodo(todoText);
                    System.out.println("Successfully added new todo: " + todoText);
                    exchange.sendResponseHeaders(200, 0);
                } else {
                    exchange.sendResponseHeaders(400, 0);
                }
            }
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Backend server started on port " + port);
    }

    private static void ensureTableExists() {
        System.out.println("Connecting to database...");
        while (true) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS todos (id SERIAL PRIMARY KEY, content TEXT);");
                System.out.println("Database table verified successfully.");
                break;
            } catch (Exception e) {
                System.err.println("Database connection failed: " + e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ie) {}
            }
        }
    }

    private static List<String> fetchTodos() {
        List<String> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement("SELECT content FROM todos ORDER BY id ASC");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString("content"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static void saveTodo(String content) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO todos (content) VALUES (?)")) {
            stmt.setString(1, content);
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}