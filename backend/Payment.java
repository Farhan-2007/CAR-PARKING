import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.sql.*;
import java.util.Map;

/**
 * POST /api/payment  (booking_id, decision = yes | no | timeout)
 * yes     -> booking PAID
 * no      -> booking CANCELLED, slot freed, payment recorded as FAILED
 * timeout -> booking CANCELLED, slot freed
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
            String decision=f.getOrDefault("decision","").toLowerCase();
            if(!decision.equals("yes")&&!decision.equals("no")&&!decision.equals("timeout")){
                Main.send(e,400,"{\"error\":\"Invalid decision\"}");return;
            }

            SlotBooking.expireStale();   // cancels anything older than 100 s first

            try(Connection c=Main.db()){
                c.setAutoCommit(false);
                double amount; int slotId; String status;
                try(PreparedStatement q=c.prepareStatement("SELECT amount,status,slot_id FROM bookings WHERE id=? AND username=? FOR UPDATE")){
                    q.setInt(1,bookingId);q.setString(2,u);
                    try(ResultSet r=q.executeQuery()){
                        if(!r.next()){c.rollback();Main.send(e,404,"{\"error\":\"Booking not found\"}");return;}
                        amount=r.getDouble(1);status=r.getString(2);slotId=r.getInt(3);
                    }
                }
                if(status.equals("CANCELLED")){c.rollback();Main.send(e,410,"{\"error\":\"Payment time expired. Booking cancelled.\"}");return;}
                if(!status.equals("PENDING")){c.rollback();Main.send(e,409,"{\"error\":\"Booking is not pending\"}");return;}

                if(decision.equals("yes")){
                    try(PreparedStatement q=c.prepareStatement("UPDATE bookings SET status='PAID' WHERE id=?")){q.setInt(1,bookingId);q.executeUpdate();}
                    try(PreparedStatement q=c.prepareStatement("INSERT INTO payments(booking_id,amount,status,paid_at) VALUES(?,?,'SUCCESS',NOW())")){q.setInt(1,bookingId);q.setDouble(2,amount);q.executeUpdate();}
                    c.commit();
                    Main.send(e,200,"{\"message\":\"Payment successful\",\"booking_id\":"+bookingId+",\"amount\":"+amount+"}");
                } else {
                    try(PreparedStatement q=c.prepareStatement("UPDATE bookings SET status='CANCELLED' WHERE id=?")){q.setInt(1,bookingId);q.executeUpdate();}
                    try(PreparedStatement q=c.prepareStatement("UPDATE slots SET status='AVAILABLE' WHERE id=?")){q.setInt(1,slotId);q.executeUpdate();}
                    if(decision.equals("no")){
                        try(PreparedStatement q=c.prepareStatement("INSERT INTO payments(booking_id,amount,status,paid_at) VALUES(?,?,'FAILED',NULL)")){q.setInt(1,bookingId);q.setDouble(2,amount);q.executeUpdate();}
                    }
                    c.commit();
                    Main.send(e,200,"{\"message\":\"Payment unsuccessful\",\"cancelled\":true}");
                }
            }
        } catch(Exception ex) {
            Main.send(e,500,"{\"error\":\"Payment failed\"}");
        }
    }
}