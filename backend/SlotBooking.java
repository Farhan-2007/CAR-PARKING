import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.sql.*;
import java.util.Map;

/**
 * Handles everything to do with parking slots:
 * - GET  /api/slots       -> list every slot and its status
 * - POST /api/book        -> reserve an available slot (requires login)
 * - GET  /api/mybookings  -> the caller's own booking history (requires login)
 */
public class SlotBooking {
    static void handle(HttpExchange e) throws IOException {
        if(e.getRequestMethod().equalsIgnoreCase("OPTIONS")){Main.cors(e);e.sendResponseHeaders(204,-1);return;}
        try {
            String path=e.getRequestURI().getPath();
            String method=e.getRequestMethod();
            if(path.equals("/api/slots")&&method.equalsIgnoreCase("GET")){slots(e);return;}
            if(path.equals("/api/book")&&method.equalsIgnoreCase("POST")){book(e);return;}
            if(path.equals("/api/mybookings")&&method.equalsIgnoreCase("GET")){myBookings(e);return;}
            Main.send(e,404,"{\"error\":\"Not found\"}");
        } catch(Exception ex) {
            Main.send(e,500,"{\"error\":\"Server error\"}");
        }
    }

    /** Returns every slot with its current status: AVAILABLE or BOOKED. */
    static void slots(HttpExchange e) throws Exception {
        StringBuilder x=new StringBuilder("[");
        try(Connection c=Main.db();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,status FROM slots ORDER BY id")){
            boolean first=true;
            while(r.next()){
                if(!first)x.append(",");
                first=false;
                x.append("{\"id\":").append(r.getInt(1)).append(",\"status\":\"").append(r.getString(2)).append("\"}");
            }
        }
        x.append("]");
        Main.send(e,200,x.toString());
    }

    /**
     * Books a slot for the logged-in user. Uses SELECT ... FOR UPDATE inside a
     * transaction so two people booking the same slot at the same time can't
     * both succeed (the second request sees it as no longer AVAILABLE).
     */
    static void book(HttpExchange e) throws Exception {
        String u=Authentication.user(e);
        if(u==null){Main.send(e,401,"{\"error\":\"Login required\"}");return;}
        Map<String,String> f=Main.form(Main.body(e));
        int slot=Integer.parseInt(f.getOrDefault("slot_id","0"));
        if(slot<1){Main.send(e,400,"{\"error\":\"Invalid slot\"}");return;}
        try(Connection c=Main.db()){
            c.setAutoCommit(false);
            try(PreparedStatement q=c.prepareStatement("SELECT status FROM slots WHERE id=? FOR UPDATE")){
                q.setInt(1,slot);
                try(ResultSet r=q.executeQuery()){
                    if(!r.next()||!r.getString(1).equals("AVAILABLE")){c.rollback();Main.send(e,409,"{\"error\":\"Slot unavailable\"}");return;}
                }
            }
            double amount=50.0;
            try(PreparedStatement q=c.prepareStatement("UPDATE slots SET status='BOOKED' WHERE id=?")){q.setInt(1,slot);q.executeUpdate();}
            int id;
            try(PreparedStatement q=c.prepareStatement("INSERT INTO bookings(username,slot_id,amount,status) VALUES(?,?,?,'PENDING')",Statement.RETURN_GENERATED_KEYS)){
                q.setString(1,u);q.setInt(2,slot);q.setDouble(3,amount);q.executeUpdate();
                try(ResultSet r=q.getGeneratedKeys()){r.next();id=r.getInt(1);}
            }
            c.commit();
            Main.send(e,201,"{\"booking_id\":"+id+",\"slot_id\":"+slot+",\"amount\":"+amount+",\"status\":\"PENDING\"}");
        }
    }

    /** Returns the logged-in user's own bookings, newest first. */
    static void myBookings(HttpExchange e) throws Exception {
        String u=Authentication.user(e);
        if(u==null){Main.send(e,401,"{\"error\":\"Login required\"}");return;}
        StringBuilder x=new StringBuilder("[");
        try(Connection c=Main.db();PreparedStatement q=c.prepareStatement(
                "SELECT id,slot_id,amount,status,created_at FROM bookings WHERE username=? ORDER BY created_at DESC")){
            q.setString(1,u);
            try(ResultSet r=q.executeQuery()){
                boolean first=true;
                while(r.next()){
                    if(!first)x.append(",");
                    first=false;
                    x.append("{\"id\":").append(r.getInt(1))
                     .append(",\"slot_id\":").append(r.getInt(2))
                     .append(",\"amount\":").append(r.getDouble(3))
                     .append(",\"status\":\"").append(r.getString(4)).append("\"")
                     .append(",\"created_at\":\"").append(r.getTimestamp(5)).append("\"}");
                }
            }
        }
        x.append("]");
        Main.send(e,200,x.toString());
    }
}