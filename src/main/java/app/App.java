package app;

import io.jooby.*;
import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

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
            app.setServerOptions(new ServerOptions().setSecurePort(443).setMaxRequestSize(10485760 * 1000) // 10mB *
                                                                                                           // 1000
                    .setSsl(SslOptions.x509(certPath, keyPath)));
        } else {
            // no ssl certificates found, run on port meant for local development
            System.out.println("running in local mode, 8080 only");
            app.setServerOptions(new ServerOptions().setMaxRequestSize(10485760 * 1000) // 10mB * 1000
                    .setPort(8090));
        }

        // allow all cors
        app.decorator(new CorsHandler());

        // address to retrieve a list of all elections stored in the database
        app.post("/getElection", ctx -> {
            String id = ctx.form("id").value();
            // int id = ctx.path("id").intValue();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query("SELECT * from DigiData.election WHERE DigiData.election.id = " + id);
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.post("/getCurrentElections", ctx -> {
            ctx.setResponseType(MediaType.json);
            java.sql.Date date = new java.sql.Date(Calendar.getInstance().getTime().getTime());
            // gather results from the database
            ResultSet results = Database.query("SELECT * from DigiData.election WHERE DigiData.election.start_date < '"
                    + date + "'AND DigiData.election.end_date > '" + date + "'");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.post("/getPreviousElections", ctx -> {
            ctx.setResponseType(MediaType.json);
            java.sql.Date date = new java.sql.Date(Calendar.getInstance().getTime().getTime());
            // gather results from the database
            ResultSet results = Database
                    .query("SELECT * from DigiData.election WHERE DigiData.election.end_date < '" + date + "'");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.post("/getNumQuestions", ctx -> {
            String id = ctx.form("id").value();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query("SELECT COUNT(question.id)\n" + "FROM DigiData.question\n"
                    + "INNER JOIN DigiData.election ON DigiData.question.election_id = DigiData.election.id\n"
                    + "WHERE DigiData.election.id = " + id + "\n");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.post("/getQuestions", ctx -> {
            String id = ctx.form("id").value();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query("SELECT \n" + "DigiData.question.name, \n" + "DigiData.question.id,\n"
                    + "DigiData.question.type,\n" + "DigiData.option.name\n" + "FROM DigiData.option\n"
                    + "INNER JOIN DigiData.question ON DigiData.question.id = DigiData.option.question_id\n"
                    + "WHERE DigiData.question.election_id = " + id + "\n");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.post("/getQuestionOptions", ctx -> {
            String qid = ctx.form("qid").value();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query(
                    "SELECT *\n" + "FROM DigiData.option\n" + "WHERE DigiData.option.question_id = " + qid + "\n");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.post("/getBoolQuestionType", ctx -> {
            String qid = ctx.form("qid").value();
            ctx.setResponseType(MediaType.json);
            // gather results from the database
            ResultSet results = Database.query("SELECT DigiData.question.id, DigiData.question.type\n"
                    + "FROM DigiData.question \n" + "WHERE DigiData.question.id = " + qid + "\n");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.post("/getNumQuestionOptions", ctx -> {
            String qid = ctx.form("qid").value();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query("SELECT COUNT(DigiData.option.id)\n" + "FROM DigiData.option\n"
                    + "INNER JOIN DigiData.question ON DigiData.option.question_id = DigiData.question.id\n"
                    + "WHERE DigiData.question.id = " + qid + "\n");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.post("/getListUsersVoted", ctx -> {
            String id = ctx.form("id").value();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query("SELECT DigiData.user.id, DigiData.user.name\n" + "FROM DigiData.user\n"
                    + "INNER JOIN DigiData.answer ON DigiData.answer.user_id = DigiData.user.id\n"
                    + "INNER JOIN DigiData.option ON DigiData.answer.option_id = DigiData.option.id\n"
                    + "INNER JOIN DigiData.question ON DigiData.option.question_id = DigiData.question.id\n"
                    + "INNER JOIN DigiData.election ON DigiData.question.election_id = DigiData.election.id\n"
                    + "WHERE DigiData.election.id = " + id + "\n");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.post("/getBoolUserVoted", ctx -> {
            String id = ctx.form("id").value();
            String uid = ctx.form("uid").value();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query("SELECT DigiData.user.id, DigiData.user.name\n" + "FROM DigiData.user\n"
                    + "INNER JOIN DigiData.answer ON DigiData.answer.user_id = DigiData.user.id\n"
                    + "INNER JOIN DigiData.option ON DigiData.answer.option_id = DigiData.option.id\n"
                    + "INNER JOIN DigiData.question ON DigiData.option.question_id = DigiData.question.id\n"
                    + "INNER JOIN DigiData.election ON DigiData.question.election_id = DigiData.election.id\n"
                    + "WHERE DigiData.election.id = " + id + " AND DigiData.user.id = " + uid + "\n");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.post("/getListAllAnswers", ctx -> {
            String id = ctx.form("id").value();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query("SELECT \n"
                    + "DigiData.question.name AS \"question_name\", \n"
                    + "DigiData.question.type AS \"question_type\", \n" 
                    + "DigiData.option.name AS \"option_name\",\n"
                    + "DigiData.answer.user_id AS \"user_id\",\n" + "DigiData.answer.response AS \"response\"\n"
                    + "FROM DigiData.answer\n"
                    + "INNER JOIN DigiData.option ON DigiData.answer.option_id = DigiData.option.id\n"
                    + "INNER JOIN DigiData.question ON DigiData.option.question_id = DigiData.question.id\n"
                    + "INNER JOIN DigiData.election ON DigiData.question.election_id = DigiData.election.id\n"
                    + "WHERE DigiData.election.id = " + id);
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });
        app.post("/getNumVotesForOption", ctx -> {
            String oid = ctx.form("oid").value();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query(
                    "SELECT COUNT(DigiData.answer.id) FROM DigiData.answer INNER JOIN DigiData.option ON DigiData.answer.option_id = DigiData.option.id WHERE DigiData.option.id = "
                            + oid);
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });

        app.get("/persistSubmitVote", ctx -> {
            String uid = ctx.form("uid").value();
            String oid = ctx.form("oid").value();
            String res = ctx.form("res").value();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database
                    .query("INSERT INTO `DigiData`.`answer` (`user_id`, `option_id`, `response`) VALUES ('" + uid
                            + "', '" + oid + "', '" + res + "');");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });

        // start the server
        app.start();
    }
}
