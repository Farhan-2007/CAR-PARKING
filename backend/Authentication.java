
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Authentication {

    static final ConcurrentHashMap<String, String> sessions =
            new ConcurrentHashMap<>();

    static String hash(String s) throws Exception {
        byte[] b = MessageDigest.getInstance("SHA-256")
                .digest(s.getBytes(StandardCharsets.UTF_8));

        StringBuilder x = new StringBuilder();

        for (byte v : b) {
            x.append(String.format("%02x", v));
        }

        return x.toString();
    }

    static String user(HttpExchange e) {
        String h = e.getRequestHeaders().getFirst("Authorization");

        if (h == null || !h.startsWith("Bearer ")) {
            return null;
        }

        return sessions.get(h.substring(7));
    }

    static boolean isAdmin(HttpExchange e) {
        String username = user(e);

        if (username == null) {
            return false;
        }

        try (
                Connection c = Main.db();
                PreparedStatement q = c.prepareStatement(
                        "SELECT is_admin FROM users WHERE username = ?"
                )
        ) {
            q.setString(1, username);

            try (ResultSet r = q.executeQuery()) {
                return r.next() && r.getBoolean("is_admin");
            }
        } catch (SQLException ex) {
            return false;
        }
    }

    static void handle(HttpExchange e) throws IOException {
        if (e.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            Main.cors(e);
            e.sendResponseHeaders(204, -1);
            return;
        }

        if (!e.getRequestMethod().equalsIgnoreCase("POST")) {
            Main.send(e, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            Map<String, String> f = Main.form(Main.body(e));
            String u = f.getOrDefault("username", "").trim();
            String p = f.getOrDefault("password", "");

            try (
                    Connection c = Main.db();
                    PreparedStatement q = c.prepareStatement(
                            "SELECT password_hash FROM users WHERE username = ?"
                    )
            ) {
                q.setString(1, u);

                try (ResultSet r = q.executeQuery()) {
                    if (!r.next() || !r.getString(1).equals(hash(p))) {
                        Main.send(e, 401, "{\"error\":\"Invalid login\"}");
                        return;
                    }
                }
            }

            String t = UUID.randomUUID().toString();
            sessions.put(t, u);

            Main.send(
                    e,
                    200,
                    "{\"token\":\"" + t +
                    "\",\"username\":\"" + Main.jsonEscape(u) + "\"}"
            );

        } catch (Exception ex) {
            Main.send(e, 500, "{\"error\":\"Login failed\"}");
        }
    }

    static void logout(HttpExchange e) throws IOException {
        if (e.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            Main.cors(e);
            e.sendResponseHeaders(204, -1);
            return;
        }

        String h = e.getRequestHeaders().getFirst("Authorization");

        if (h != null && h.startsWith("Bearer ")) {
            sessions.remove(h.substring(7));
        }

        Main.send(e, 200, "{\"message\":\"Logged out\"}");
    }
}
