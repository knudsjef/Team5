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
        app.get("/getListElections", ctx -> {
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query("SELECT * from DigiData.election");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.get("/getNumQuestions", ctx -> {
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query("SELECT COUNT(question.id)\n" +
                    "FROM DigiData.question\n" +
                    "INNER JOIN DigiData.election ON DigiData.question.election_id = DigiData.election.id\n" +
                    "WHERE DigiData.election.id = 1\n");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
//        app.get("/getBoolQuestionType", ctx -> {
//            ctx.setResponseType(MediaType.json);
//
//            // gather results from the database
//            ResultSet results = Database.query("SELECT DigiData.question.id, DigiData.question.type\n" +
//                    "FROM DigiData.question \n" +
//                    "WHERE DigiData.question.id = 1\n");
//            // return the results as json for easy processing on the frontend
//            return Database.getJSONFromResultSet(results,"results");
//        });
//        app.get("/getNumQuestionOptions", ctx -> {
//            ctx.setResponseType(MediaType.json);
//
//            // gather results from the database
//            ResultSet results = Database.query("SELECT COUNT(DigiData.option.id)\n" +
//                    "FROM DigiData.option\n" +
//                    "INNER JOIN DigiData.question ON DigiData.option.question_id = DigiData.question.id\n" +
//                    "WHERE DigiData.question.id = 1\n");
//            // return the results as json for easy processing on the frontend
//            return Database.getJSONFromResultSet(results,"results");
//        });
//        app.get("/getListUsersVoted", ctx -> {
//            ctx.setResponseType(MediaType.json);
//
//            // gather results from the database
//            ResultSet results = Database.query("SELECT DigiData.user.id, DigiData.user.name\n" +
//                    "FROM DigiData.user\n" +
//                    "INNER JOIN DigiData.answer ON DigiData.answer.user_id = DigiData.user.id\n" +
//                    "INNER JOIN DigiData.option ON DigiData.answer.option_id = DigiData.option.id\n" +
//                    "INNER JOIN DigiData.question ON DigiData.option.question_id = DigiData.question.id\n" +
//                    "INNER JOIN DigiData.election ON DigiData.question.election_id = DigiData.election.id\n" +
//                    "WHERE DigiData.election.id = 1\n");
//            // return the results as json for easy processing on the frontend
//            return Database.getJSONFromResultSet(results,"results");
//        });
//        app.get("/getBoolUserVoted", ctx -> {
//            ctx.setResponseType(MediaType.json);
//
//            // gather results from the database
//            ResultSet results = Database.query("SELECT DigiData.user.id, DigiData.user.name\n" +
//                    "FROM DigiData.user\n" +
//                    "INNER JOIN DigiData.answer ON DigiData.answer.user_id = DigiData.user.id\n" +
//                    "INNER JOIN DigiData.option ON DigiData.answer.option_id = DigiData.option.id\n" +
//                    "INNER JOIN DigiData.question ON DigiData.option.question_id = DigiData.question.id\n" +
//                    "INNER JOIN DigiData.election ON DigiData.question.election_id = DigiData.election.id\n" +
//                    "WHERE DigiData.election.id = 1 AND DigiData.user.id = 1\n");
//            // return the results as json for easy processing on the frontend
//            return Database.getJSONFromResultSet(results,"results");
//        });
//        app.get("/getListAllAnswers", ctx -> {
//            ctx.setResponseType(MediaType.json);
//
//            // gather results from the database
//            ResultSet results = Database.query("SELECT DigiData.answer.id, DigiData.answer.user_id, DigiData.answer.option_id, DigiData.answer.response\n" +
//                    "FROM DigiData.answer\n" +
//                    "INNER JOIN DigiData.option ON DigiData.answer.option_id = DigiData.option.id\n" +
//                    "INNER JOIN DigiData.question ON DigiData.option.question_id = DigiData.question.id\n" +
//                    "INNER JOIN DigiData.election ON DigiData.question.election_id = DigiData.election.id\n" +
//                    "WHERE DigiData.election.id = 1\n");
//            // return the results as json for easy processing on the frontend
//            return Database.getJSONFromResultSet(results,"results");
//        });
//        app.get("/getNumVotesForOption", ctx -> {
//            ctx.setResponseType(MediaType.json);
//
//            // gather results from the database
//            ResultSet results = Database.query("SELECT COUNT(DigiData.answer.id) FROM DigiData.answer INNER JOIN DigiData.option ON DigiData.answer.option_id = DigiData.option.id WHERE DigiData.option.id = 1");
//            // return the results as json for easy processing on the frontend
//            return Database.getJSONFromResultSet(results,"results");
//        });
//        app.get("/persistSubmitVote", ctx -> {
//            ctx.setResponseType(MediaType.json);
//
//            // gather results from the database
//            ResultSet results = Database.query("INSERT INTO `DigiData`.`answer` (`user_id`, `option_id`, `response`) VALUES ('1', '7', 'Y');");
//            // return the results as json for easy processing on the frontend
//            return Database.getJSONFromResultSet(results,"results");
//        });


        // start the server
        app.start();
    }
}


