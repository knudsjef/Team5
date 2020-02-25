package app;

import io.jooby.*;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;

public class App {

    public static void main(final String[] args) throws IOException, SQLException, ClassNotFoundException {
        // server setup
        Jooby app = new Jooby();

        // ssl
        String certPath = "/etc/letsencrypt/live/nimmo.us/fullchain.pem"; // cert
        String keyPath = "/etc/letsencrypt/live/nimmo.us/privkey.pem"; // key
        if (new File(certPath).exists()) {
            System.out.println("running in secure mode, 8080 and 8443");
            app.setServerOptions(new ServerOptions()
                    .setSecurePort(443)
                    .setMaxRequestSize(10485760*1000) // 10mB * 1000
                    .setSsl(SslOptions.x509(certPath, keyPath))
            );
        } else {
            System.out.println("running in local mode, 8080 only");
            app.setServerOptions(new ServerOptions()
                    .setMaxRequestSize(10485760*1000) // 10mB * 1000
                    .setPort(8080)
            );
        }

        // allow all cors
        app.decorator(new CorsHandler());

        // test page
        app.get("/getElections", ctx -> {
            ctx.setResponseType(MediaType.json);

            // database setup
            ResultSet results = Database.query("SELECT * from DigiData.election");
            return Database.getJSONFromResultSet(results,"results");
        });

        app.start();
    }
}


