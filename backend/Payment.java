import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.sql.*;
import java.util.Map;

/**
 * Handles POST /api/payment — marks a PENDING booking as PAID and records
 * a payment row. Wrapped in a transaction with a row lock so a booking
 * can't be paid for twice at the same time.
 */
public class Payment {
    static void handle(HttpExchange e) throws IOException {
        if(e.getRequestMethod().equalsIgnoreCase("OPTIONS")){Main.cors(e);e.sendResponseHeaders(204,-1);return;}
        if(!e.getRequestMethod().equalsIgnoreCase("POST")){Main.send(e,405,"{\"error\":\"Method not allowed\"}");return;}
        try {
            String u=Authentication.user(e);
            if(u==null){Main.send(e,401,"{\"error\":\"Login required\"}");return;}
            Map<String,String> f=Main.form(Main.body(e));
            int bookingId=Integer.parseInt(f.getOrDefault("booking_id","0"));
            try(Connection c=Main.db()){
                c.setAutoCommit(false);
                double amount;
                try(PreparedStatement q=c.prepareStatement("SELECT amount,status FROM bookings WHERE id=? AND username=? FOR UPDATE")){
                    q.setInt(1,bookingId);q.setString(2,u);
                    try(ResultSet r=q.executeQuery()){
                        if(!r.next()){c.rollback();Main.send(e,404,"{\"error\":\"Booking not found\"}");return;}
                        if(!r.getString(2).equals("PENDING")){c.rollback();Main.send(e,409,"{\"error\":\"Booking is not pending\"}");return;}
                        amount=r.getDouble(1);
                    }
                }
                try(PreparedStatement q=c.prepareStatement("UPDATE bookings SET status='PAID' WHERE id=?")){q.setInt(1,bookingId);q.executeUpdate();}
                try(PreparedStatement q=c.prepareStatement("INSERT INTO payments(booking_id,amount,status,paid_at) VALUES(?,?, 'SUCCESS', NOW())")){q.setInt(1,bookingId);q.setDouble(2,amount);q.executeUpdate();}
                c.commit();
                Main.send(e,200,"{\"message\":\"Payment successful\",\"booking_id\":"+bookingId+",\"amount\":"+amount+"}");
            }
        } catch(Exception ex) {
            Main.send(e,500,"{\"error\":\"Payment failed\"}");
        }
    }
}