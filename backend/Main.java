import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;

public class Main {

    static final String DB_URL = System.getenv().getOrDefault(
            "DB_URL",
            "jdbc:mysql://localhost:3306/carparking"
    );

    static final String DB_USER = System.getenv().getOrDefault(
            "DB_USER",
            "root"
    );

    static final String DB_PASSWORD = System.getenv().getOrDefault(
            "DB_PASSWORD",
            "farhan@2710"
    );

    static final int PORT = Integer.parseInt(
            System.getenv().getOrDefault("PORT", "8080")
    );

    static Connection db() throws SQLException {
        return DriverManager.getConnection(
                DB_URL,
                DB_USER,
                DB_PASSWORD
        );
    }

    static String body(HttpExchange e) throws IOException {
        return new String(
                e.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        );
    }

    static Map<String, String> form(String s) {
        Map<String, String> m = new HashMap<>();

        for (String p : s.split("&")) {
            String[] a = p.split("=", 2);

            if (a.length == 2) {
                m.put(
                        a[0],
                        java.net.URLDecoder.decode(
                                a[1],
                                StandardCharsets.UTF_8
                        )
                );
            }
        }

        return m;
    }

    static void cors(HttpExchange e) {
        e.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
        );

        e.getResponseHeaders().set(
                "Access-Control-Allow-Headers",
                "Content-Type,Authorization"
        );

        e.getResponseHeaders().set(
                "Access-Control-Allow-Methods",
                "GET,POST,OPTIONS"
        );
    }

    static void send(
            HttpExchange e,
            int code,
            String text
    ) throws IOException {

        cors(e);

        byte[] b = text.getBytes(StandardCharsets.UTF_8);

        e.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );

        e.sendResponseHeaders(code, b.length);

        try (OutputStream o = e.getResponseBody()) {
            o.write(b);
        }
    }

    static void setup() throws SQLException {

        try (
                Connection c = db();
                Statement s = c.createStatement()
        ) {

            s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS users(" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "username VARCHAR(50) UNIQUE NOT NULL," +
                    "password_hash VARCHAR(64) NOT NULL)"
            );

            s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS slots(" +
                    "id INT PRIMARY KEY," +
                    "status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE')"
            );

            s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS bookings(" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "username VARCHAR(50) NOT NULL," +
                    "slot_id INT NOT NULL," +
                    "amount DECIMAL(10,2) NOT NULL," +
                    "status VARCHAR(20) NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS payments(" +
                    "id INT PRIMARY KEY AUTO_INCREMENT," +
                    "booking_id INT NOT NULL," +
                    "amount DECIMAL(10,2) NOT NULL," +
                    "status VARCHAR(20) NOT NULL," +
                    "paid_at TIMESTAMP NULL)"
            );

            try (
                    ResultSet r =
                            s.executeQuery("SELECT COUNT(*) FROM slots")
            ) {

                r.next();

                if (r.getInt(1) == 0) {

                    try (
                            PreparedStatement p =
                                    c.prepareStatement(
                                            "INSERT INTO slots(id,status) " +
                                            "VALUES(?,'AVAILABLE')"
                                    )
                    ) {

                        for (int i = 1; i <= 20; i++) {
                            p.setInt(1, i);
                            p.addBatch();
                        }

                        p.executeBatch();
                    }
                }
            }
        }
    }

    static String jsonEscape(String s) {
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    /*
     * Serves the frontend.
     *
     * http://localhost:8080/
     *        ↓
     * frontend/index.html
     */
    static void serveFrontend(HttpExchange e)
            throws IOException {

        String requestPath =
                e.getRequestURI().getPath();

        if (requestPath.equals("/")) {
            requestPath = "/index.html";
        }

        Path file = Path.of(
                "..",
                "frontend",
                requestPath.substring(1)
        ).normalize();

        if (
                !Files.exists(file) ||
                Files.isDirectory(file)
        ) {

            String response =
                    "404 - Page Not Found";

            byte[] data =
                    response.getBytes(StandardCharsets.UTF_8);

            e.sendResponseHeaders(
                    404,
                    data.length
            );

            try (
                    OutputStream out =
                            e.getResponseBody()
            ) {
                out.write(data);
            }

            return;
        }

        String contentType =
                "text/html; charset=UTF-8";

        if (requestPath.endsWith(".css")) {

            contentType =
                    "text/css; charset=UTF-8";

        } else if (requestPath.endsWith(".js")) {

            contentType =
                    "application/javascript; charset=UTF-8";

        } else if (requestPath.endsWith(".png")) {

            contentType = "image/png";

        } else if (
                requestPath.endsWith(".jpg") ||
                requestPath.endsWith(".jpeg")
        ) {

            contentType = "image/jpeg";

        } else if (requestPath.endsWith(".svg")) {

            contentType = "image/svg+xml";
        }

        byte[] data =
                Files.readAllBytes(file);

        e.getResponseHeaders().set(
                "Content-Type",
                contentType
        );

        e.sendResponseHeaders(
                200,
                data.length
        );

        try (
                OutputStream out =
                        e.getResponseBody()
        ) {
            out.write(data);
        }
    }

    public static void main(String[] args)
            throws Exception {

        setup();

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(PORT),
                        0
                );

        // Frontend
        server.createContext(
                "/",
                Main::serveFrontend
        );

        // API endpoints
        server.createContext(
                "/api/register",
                UserReg::handle
        );

        server.createContext(
                "/api/login",
                Authentication::handle
        );

        server.createContext(
                "/api/logout",
                Authentication::logout
        );

        server.createContext(
                "/api/slots",
                SlotBooking::handle
        );

        server.createContext(
                "/api/book",
                SlotBooking::handle
        );

        server.createContext(
                "/api/mybookings",
                SlotBooking::handle
        );

        server.createContext(
                "/api/payment",
                Payment::handle
        );

        server.setExecutor(
                Executors.newCachedThreadPool()
        );

        server.start();

        System.out.println(
                "Backend running on http://localhost:" + PORT
        );

        System.out.println(
                "Website available at http://localhost:" + PORT + "/"
        );
    }
}