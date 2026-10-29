
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.sql.*;
import java.math.BigDecimal;

public class AdminBookings {

    static void handle(HttpExchange e) throws IOException {

        if (e.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            Main.cors(e);
            e.sendResponseHeaders(204, -1);
            return;
        }

        if (!e.getRequestMethod().equalsIgnoreCase("GET")) {
            Main.send(e, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        if (Authentication.user(e) == null) {
            Main.send(e, 401, "{\"error\":\"Login required\"}");
            return;
        }

        if (!Authentication.isAdmin(e)) {
            Main.send(e, 403, "{\"error\":\"Admin access required\"}");
            return;
        }

        listBookings(e);
    }

    static void listBookings(HttpExchange e) throws IOException {

        StringBuilder json = new StringBuilder("[");

        try (
                Connection c = Main.db();
                PreparedStatement q = c.prepareStatement(
                    "SELECT id, username, slot_id, amount, status, created_at " +
                    "FROM bookings ORDER BY created_at DESC, id DESC"
                );
                ResultSet r = q.executeQuery()
        ) {

            boolean first = true;

            while (r.next()) {

                if (!first) {
                    json.append(",");
                }

                first = false;

                json.append("{")
                    .append("\"id\":").append(r.getInt("id")).append(",")
                    .append("\"username\":\"")
                    .append(Main.jsonEscape(r.getString("username")))
                    .append("\",")
                    .append("\"slot_id\":").append(r.getInt("slot_id")).append(",")
                    .append("\"amount\":")
                    .append(r.getBigDecimal("amount").toPlainString())
                    .append(",")
                    .append("\"status\":\"")
                    .append(Main.jsonEscape(r.getString("status")))
                    .append("\",")
                    .append("\"created_at\":");

                Timestamp createdAt = r.getTimestamp("created_at");

                if (createdAt == null) {
                    json.append("null");
                } else {
                    json.append("\"")
                        .append(Main.jsonEscape(createdAt.toString()))
                        .append("\"");
                }

                json.append("}");
            }

            json.append("]");

            Main.send(e, 200, json.toString());

        } catch (SQLException ex) {
            Main.send(
                    e,
                    500,
                    "{\"error\":\"Could not retrieve booking records\"}"
            );
        }
    }
}
