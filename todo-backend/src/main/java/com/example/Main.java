package com.example;

import com.sun.net.httpserver.HttpServer;
import io.nats.client.Connection;
import io.nats.client.Nats;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.sql.*;

public class Main {
    private static final String DB_URL = "jdbc:postgresql://todo-postgres-svc.project:5432/todos";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = System.getenv("DB_PASSWORD");
    private static boolean isHealthy = true;

     private static Connection nc;

    public static void main(String[] args) throws IOException {
        try {
            Class.forName("org.postgresql.Driver");
             String natsUrl = System.getenv("NATS_URL");
            if (natsUrl == null) natsUrl = "nats://my-nats.nats.svc.cluster.local:4222";
            nc = Nats.connect(natsUrl);
            System.out.println("Connected to NATS server.");
        } catch (Exception e) {
            System.out.println("Error initializing DB or NATS: " + e.getMessage());
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
                    if (parts.length > 1) todoText = java.net.URLDecoder.decode(parts[1], "UTF-8");
                    else todoText = "";
                }

                if (todoText.length() > 140) {
                    String response = "Rejected: Todo cannot exceed 140 characters.";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    exchange.getResponseBody().write(response.getBytes());
                } else if (!todoText.isEmpty()) {
                    saveTodo(todoText);

                    publishToNats("A todo was created: " + todoText);

                    exchange.sendResponseHeaders(200, 0);
                } else {
                    exchange.sendResponseHeaders(400, 0);
                }
            } else if ("PUT".equalsIgnoreCase(exchange.getRequestMethod()) && path.startsWith("/todos/")) {
                String idStr = path.substring("/todos/".length());
                try {
                    int id = Integer.parseInt(idStr);
                    markTodoDone(id);

                     publishToNats("A todo was marked as done (ID: " + id + ")");

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
            String response = isHealthy ? "OK" : "Error";
            exchange.sendResponseHeaders(isHealthy ? 200 : 500, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Backend server started on port " + port);
        ensureTableExists();
    }

    private static void publishToNats(String message) {
        if (nc != null) {
            nc.publish("todo_updates", message.getBytes());
            System.out.println("Published to NATS: " + message);
        }
    }


    private static void ensureTableExists() {
        while (true) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS todos (id SERIAL PRIMARY KEY, content TEXT, done BOOLEAN DEFAULT FALSE);");
                try { stmt.execute("ALTER TABLE todos ADD COLUMN done BOOLEAN DEFAULT FALSE;"); } catch (Exception ignored) {}
                break;
            } catch (Exception e) {
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
                String content = rs.getString("content");
                if (content != null) content = content.replace("\"", "\\\"").replace("\n", " ");
                else content = "";
                sb.append("{\"id\":").append(rs.getInt("id"))
                        .append(",\"content\":\"").append(content).append("\"")
                        .append(",\"done\":").append(rs.getBoolean("done")).append("}");
                first = false;
            }
        } catch (Exception e) {}
        sb.append("]");
        return sb.toString();
    }

    private static void saveTodo(String content) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO todos (content) VALUES (?)")) {
            stmt.setString(1, content);
            stmt.executeUpdate();
        } catch (Exception e) {}
    }

    private static void markTodoDone(int id) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement("UPDATE todos SET done = TRUE WHERE id = ?")) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (Exception e) {}
    }
}