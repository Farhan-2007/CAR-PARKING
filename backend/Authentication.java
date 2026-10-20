import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles login (/api/login) and logout (/api/logout).
 * Sessions are kept in memory as token -> username. A restart of the
 * server clears everyone's session, which is fine for a mini project
 * but wouldn't be acceptable in production (use a DB or JWT there).
 */
public class Authentication {
    static final ConcurrentHashMap<String,String> sessions=new ConcurrentHashMap<>();

    /** One-way SHA-256 hash used for storing/checking passwords (never store plain text). */
    static String hash(String s) throws Exception {
        byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder x=new StringBuilder();
        for(byte v:b)x.append(String.format("%02x",v));
        return x.toString();
    }

    /** Reads the "Authorization: Bearer <token>" header and resolves it to a username, or null if not logged in. */
    static String user(HttpExchange e) {
        String h=e.getRequestHeaders().getFirst("Authorization");
        if(h==null||!h.startsWith("Bearer ")) return null;
        return sessions.get(h.substring(7));
    }

    /** POST /api/login — verifies credentials and issues a session token. */
    static void handle(HttpExchange e) throws IOException {
        if(e.getRequestMethod().equalsIgnoreCase("OPTIONS")){Main.cors(e);e.sendResponseHeaders(204,-1);return;}
        if(!e.getRequestMethod().equalsIgnoreCase("POST")){Main.send(e,405,"{\"error\":\"Method not allowed\"}");return;}
        try {
            Map<String,String> f=Main.form(Main.body(e));
            String u=f.getOrDefault("username","").trim();
            String p=f.getOrDefault("password","");
            try(Connection c=Main.db();PreparedStatement q=c.prepareStatement("SELECT password_hash FROM users WHERE username=?")){
                q.setString(1,u);
                try(ResultSet r=q.executeQuery()){
                    if(!r.next()||!r.getString(1).equals(hash(p))){Main.send(e,401,"{\"error\":\"Invalid login\"}");return;}
                }
            }
            String t=UUID.randomUUID().toString();
            sessions.put(t,u);
            Main.send(e,200,"{\"token\":\""+t+"\",\"username\":\""+Main.jsonEscape(u)+"\"}");
        } catch(Exception ex) {
            Main.send(e,500,"{\"error\":\"Login failed\"}");
        }
    }

    /** POST /api/logout — invalidates the caller's session token, if any. */
    static void logout(HttpExchange e) throws IOException {
        if(e.getRequestMethod().equalsIgnoreCase("OPTIONS")){Main.cors(e);e.sendResponseHeaders(204,-1);return;}
        String h=e.getRequestHeaders().getFirst("Authorization");
        if(h!=null && h.startsWith("Bearer ")) sessions.remove(h.substring(7));
        Main.send(e,200,"{\"message\":\"Logged out\"}");
    }
}