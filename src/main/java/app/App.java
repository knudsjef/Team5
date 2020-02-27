package app;

import io.jooby.*;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * server to act as an in-between for the website and the database
 */
public class App {
    public static void main(final String[] args) throws IOException, SQLException, ClassNotFoundException {
        // create server
        Jooby app = new Jooby();

        // locate ssl certificates generated with letsencrypt for the nimmo.us domain
        String certPath = "/etc/letsencrypt/live/nimmo.us/fullchain.pem"; // cert
        String keyPath = "/etc/letsencrypt/live/nimmo.us/privkey.pem"; // key
        // determine if ssl certificates are available in the environment
        if (new File(certPath).exists()) {
            // ssl certificates found, run only on secure port 443
            System.out.println("running in secure mode, 8080 and 8443");
            app.setServerOptions(new ServerOptions()
                    .setSecurePort(443)
                    .setMaxRequestSize(10485760*1000) // 10mB * 1000
                    .setSsl(SslOptions.x509(certPath, keyPath))
            );
        } else {
            // no ssl certificates found, run on port meant for local development
            System.out.println("running in local mode, 8080 only");
            app.setServerOptions(new ServerOptions()
                    .setMaxRequestSize(10485760*1000) // 10mB * 1000
                    .setPort(8080)
            );
        }

        // allow all cors
        app.decorator(new CorsHandler());

        // address to retrieve a list of all elections stored in the database
        app.get("/getElections", ctx -> {
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query("SELECT * from DigiData.election");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });

        // start the server
        app.start();
    }
}


