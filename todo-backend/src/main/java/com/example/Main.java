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

public class Main {
    private static final String DB_URL = "jdbc:postgresql://todo-postgres-svc.project:5432/todos";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = System.getenv("DB_PASSWORD");

    private static boolean isHealthy = true;

    public static void main(String[] args) throws IOException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("Error: PostgreSQL JDBC Driver not found!");
            return;
        }

        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/todos", exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");

            String path = exchange.getRequestURI().getPath();

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.getResponseBody().close();
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && "/todos".equals(path)) {
                String response = fetchTodosJson();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && "/todos".equals(path)) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes()).trim();

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
                    String response = "Rejected: Todo cannot exceed 140 characters.";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    exchange.getResponseBody().write(response.getBytes());
                } else if (!todoText.isEmpty()) {
                    saveTodo(todoText);
                    exchange.sendResponseHeaders(200, 0);
                } else {
                    exchange.sendResponseHeaders(400, 0);
                }
            } else if ("PUT".equalsIgnoreCase(exchange.getRequestMethod()) && path.startsWith("/todos/")) {
                String idStr = path.substring("/todos/".length());
                try {
                    int id = Integer.parseInt(idStr);
                    markTodoDone(id);
                    exchange.sendResponseHeaders(200, 0);
                } catch (Exception e) {
                    exchange.sendResponseHeaders(400, 0);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.getResponseBody().close();
        });

        server.createContext("/healthz", exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                isHealthy = false;
                String response = "Backend is now broken!";
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

            boolean isDbConnected = false;
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1;");
                isDbConnected = true;
            } catch (Exception e) {
                isDbConnected = false;
            }

            if (isDbConnected) {
                String response = "OK";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
            } else {
                String response = "Error: DB not connected";
                exchange.sendResponseHeaders(500, response.getBytes().length);
                exchange.getResponseBody().write(response.getBytes());
            }
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Backend server started on port " + port);

        ensureTableExists();
    }

    private static void ensureTableExists() {
        System.out.println("Connecting to database...");
        while (true) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {


                stmt.execute("CREATE TABLE IF NOT EXISTS todos (id SERIAL PRIMARY KEY, content TEXT, done BOOLEAN DEFAULT FALSE);");


                try {
                    stmt.execute("ALTER TABLE todos ADD COLUMN done BOOLEAN DEFAULT FALSE;");
                } catch (Exception ignored) {

                }

                System.out.println("Database table verified successfully.");
                break;
            } catch (Exception e) {
                System.err.println("Database connection failed: " + e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ie) {}
            }
        }
    }

    private static String fetchTodosJson() {
        StringBuilder sb = new StringBuilder("[");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement("SELECT id, content, done FROM todos ORDER BY id ASC");
             ResultSet rs = stmt.executeQuery()) {
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                int id = rs.getInt("id");
                String content = rs.getString("content");
                if (content != null) {
                    content = content.replace("\"", "\\\"").replace("\n", " ");
                } else {
                    content = "";
                }
                boolean done = rs.getBoolean("done");

                sb.append("{\"id\":").append(id)
                        .append(",\"content\":\"").append(content).append("\"")
                        .append(",\"done\":").append(done).append("}");
                first = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        sb.append("]");
        return sb.toString();
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

    private static void markTodoDone(int id) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement("UPDATE todos SET done = TRUE WHERE id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
            System.out.println("Marked todo " + id + " as done.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}