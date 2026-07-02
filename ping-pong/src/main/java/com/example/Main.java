package com.example;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class Main {
    private static final String DB_URL = "jdbc:postgresql://postgres-svc.exercises:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "example";

    public static void main(String[] args) throws IOException {

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("Error: PostgreSQL JDBC Driver not found!");
            return;
        }

        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/pingpong", exchange -> {
            int currentCount = getAndIncrementCount();
            String response = "pong " + currentCount;
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        server.createContext("/pongs", exchange -> {
            String response = String.valueOf(getCount());
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        server.createContext("/healthz", exchange -> {
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
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
            } else {
                String response = "Error: DB not connected";
                exchange.sendResponseHeaders(500, response.length());
                exchange.getResponseBody().write(response.getBytes());
            }
            exchange.getResponseBody().close();
        });

        server.start();
        System.out.println("Ping-pong server started on port " + port);

        ensureTableExists();
    }

    private static void ensureTableExists() {
        while (true) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS pingpong_count (id INT PRIMARY KEY, value INT);");
                stmt.execute("INSERT INTO pingpong_count (id, value) VALUES (1, 0) ON CONFLICT (id) DO NOTHING;");
                System.out.println("Database table initialized successfully.");
                break;
            } catch (Exception e) {
                System.out.println("Database not ready yet. Retrying... Error: " + e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ie) {}
            }
        }
    }

    private static int getAndIncrementCount() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(false);
            int value = 0;
            try (PreparedStatement select = conn.prepareStatement("SELECT value FROM pingpong_count WHERE id = 1 FOR UPDATE");
                 ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    value = rs.getInt("value");
                }
            }
            try (PreparedStatement update = conn.prepareStatement("UPDATE pingpong_count SET value = value + 1 WHERE id = 1")) {
                update.executeUpdate();
            }
            conn.commit();
            return value;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int getCount() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement("SELECT value FROM pingpong_count WHERE id = 1");
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("value");
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }
}