import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.Map;

/** Handles POST /api/register — creates a new user with a hashed password. */
public class UserReg {
    static String hash(String s) throws Exception {
        byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder x=new StringBuilder();
        for(byte v:b)x.append(String.format("%02x",v));
        return x.toString();
    }

    static void handle(HttpExchange e) throws IOException {
        if(e.getRequestMethod().equalsIgnoreCase("OPTIONS")){Main.cors(e);e.sendResponseHeaders(204,-1);return;}
        if(!e.getRequestMethod().equalsIgnoreCase("POST")){Main.send(e,405,"{\"error\":\"Method not allowed\"}");return;}
        try {
            Map<String,String> f=Main.form(Main.body(e));
            String u=f.getOrDefault("username","").trim();
            String p=f.getOrDefault("password","");
            if(u.isEmpty()||p.isEmpty()){Main.send(e,400,"{\"error\":\"Username and password are required\"}");return;}
            try(Connection c=Main.db();PreparedStatement q=c.prepareStatement("INSERT INTO users(username,password_hash) VALUES(?,?)")){
                q.setString(1,u);q.setString(2,hash(p));q.executeUpdate();
            }
            Main.send(e,201,"{\"message\":\"Registration successful\"}");
        } catch(SQLException ex) {
            Main.send(e,409,"{\"error\":\"Username already exists\"}");
        } catch(Exception ex) {
            Main.send(e,500,"{\"error\":\"Registration failed\"}");
        }
    }
}