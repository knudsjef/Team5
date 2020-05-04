package app;

import io.jooby.*;
import org.json.simple.JSONValue;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLOutput;
import java.util.*;

/**
 * server to act as an in-between for the website and the database
 */
public class App {
    // <certificate random string, [user id, date]
    private static HashMap<String,String[]> certificates = new HashMap<String,String[]>();
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
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
            String id = ctx.form("id").value();
            ctx.setResponseType(MediaType.json);
            // gather results from the database
            ResultSet results = Database.query("SELECT * from DigiData.election WHERE DigiData.election.id = " + id);
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });

        app.post("/getCurrentElections", ctx -> {
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
            ctx.setResponseType(MediaType.json);
            String id = ctx.form("id").value();
            java.sql.Date date = new java.sql.Date(Calendar.getInstance().getTime().getTime());
            // gather results from the database
            ResultSet results = Database.query("SELECT DigiData.election.id, DigiData.election.name, DigiData.election.start_date, DigiData.election.end_date FROM DigiData.election\n" +
                    "INNER JOIN DigiData.user_group\n" +
                    "WHERE DigiData.election.start_date < '" + date +
                    "' AND DigiData.election.end_date > '" + date +
                    "' AND DigiData.election.group_name = DigiData.user_group.group_name\n" +
                    "AND DigiData.user_group.user_id = '" + id + "'");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });

        app.post("/getPreviousElections", ctx -> {
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
            ctx.setResponseType(MediaType.json);
            java.sql.Date date = new java.sql.Date(Calendar.getInstance().getTime().getTime());
            // gather results from the database
            ResultSet results = Database
                    .query("SELECT * from DigiData.election WHERE DigiData.election.end_date < '" + date + "'");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });

        app.post("/getNumQuestions", ctx -> {
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
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
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
            String id = ctx.form("id").value();
            ctx.setResponseType(MediaType.json);
            // gather results from the database
            ResultSet results = Database.query("SELECT \n" + "DigiData.question.name AS question_name, \n" + "DigiData.question.id,\n"
                    + "DigiData.question.type,\n" + "DigiData.option.name AS option_name\n" + "FROM DigiData.option\n"
                    + "INNER JOIN DigiData.question ON DigiData.question.id = DigiData.option.question_id\n"
                    + "WHERE DigiData.question.election_id = " + id + "\n");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });

        app.post("/getQuestionOptions", ctx -> {
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
            String qid = ctx.form("qid").value();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query(
                    "SELECT *\n" + "FROM DigiData.option\n" + "WHERE DigiData.option.question_id = " + qid + "\n");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });

        app.post("/getBoolQuestionType", ctx -> {
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
            String qid = ctx.form("qid").value();
            ctx.setResponseType(MediaType.json);
            // gather results from the database
            ResultSet results = Database.query("SELECT DigiData.question.id, DigiData.question.type\n"
                    + "FROM DigiData.question \n" + "WHERE DigiData.question.id = " + qid + "\n");
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });

        app.post("/getNumQuestionOptions", ctx -> {
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
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
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
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
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }

            java.sql.Date date = new java.sql.Date(Calendar.getInstance().getTime().getTime());
            String id = ctx.form("id").value();
            ResultSet checkDate = Database.query("SELECT DigiData.election.end_date from DigiData.election WHERE DigiData.election.id = " + id);
            checkDate.next();
            java.sql.Date endDate = checkDate.getDate(1);

            String uid = ctx.form("uid").value();
            if(endDate.getTime() < date.getTime()){
                return "{\"valid\": \"true\"}";
            }
            if(uid.equals("")||checkCertificate(ctx)) {
                ctx.setResponseType(MediaType.json);

                // gather results from the database
                ResultSet results = Database.query("SELECT COUNT(DigiData.answer.user_id)\n" + "FROM DigiData.answer\n"
                        + "INNER JOIN DigiData.option ON DigiData.answer.option_id = DigiData.option.id\n"
                        + "INNER JOIN DigiData.question ON DigiData.option.question_id = DigiData.question.id\n"
                        + "WHERE DigiData.answer.option_id = DigiData.option.id AND DigiData.option.question_id = DigiData.question.id "
                        + "AND DigiData.question.election_id = '" + id + "' AND DigiData.answer.user_id = '" + uid + "'\n");
                // return the results as json for easy processing on the frontend
                return "{\"valid\": \"true\"}";
            }
            return "{\"valid\": \"false\"}";
        });

        app.post("/getListAllAnswers", ctx -> {
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }

            String id = ctx.form("id").value();

            ctx.setResponseType(MediaType.json);
            java.sql.Date date = new java.sql.Date(Calendar.getInstance().getTime().getTime());
            ResultSet checkDate = Database.query("SELECT DigiData.election.end_date from DigiData.election WHERE DigiData.election.id = " + id);
            checkDate.next();
            java.sql.Date endDate = checkDate.getDate(1);
            if(date.getTime() > endDate.getTime() || checkCertificate(ctx)){
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
            }
            return "{\"valid\": \"false\"}";
        });
        app.post("/getNumVotesForOption", ctx -> {
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
            String oid = ctx.form("oid").value();
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query(
                    "SELECT COUNT(DigiData.answer.id) FROM DigiData.answer INNER JOIN DigiData.option ON DigiData.answer.option_id = DigiData.option.id WHERE DigiData.option.id = "
                            + oid);
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });

        app.post("/getElectionQuestionsAndOptions", ctx -> {
            String certificate = ctx.form("certificate").value();
            String userID = ctx.form("userID").value();
            if(!checkCertificate(ctx)) {
                return "{\"valid\": \"false\"}";
            }
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query("SELECT \n" +
                    "    q.id AS 'question_id',\n" +
                    "    q.name AS 'question_name',\n" +
                    "    q.type AS 'question_type',\n" +
                    "    o.name AS 'option_name',\n" +
                    "    o.id AS 'option_id'\n" +
                    "FROM DigiData.question q\n" +
                    "INNER JOIN DigiData.option o ON q.id = o.question_id\n" +
                    "WHERE q.election_id = " + ctx.form("electionID").value());
            // return the results as json for easy processing on the frontend
            return Database.getJSONFromResultSet(results,"results");
        });

        app.post("/persistSubmitVote", ctx -> {
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }

            String numQuestions = ctx.form("numOptions").value();
            String uid = ctx.form("uid").value();
            int num = Integer.valueOf(numQuestions);

            String query = "INSERT INTO DigiData.answer (user_id, option_id, response) VALUES ";
            for(int i = 1;i<=num;i++){
                query += "(" + uid + ", " + ctx.form("oid"+i).value() + ", ";
                String res = ctx.form("res"+i).value();
                query += "\"" + res + "\")";
                if(i!=num) query += ", ";
            }
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            int result = Database.statement(query);
            // return the results as json for easy processing on the frontend
            return "{\"rowsModified\": " + result + "}";
        });
        app.post("/persistInsertUser", ctx -> {
            String name = ctx.form("name").value();
            String hash = ctx.form("hash").value();
            String email = ctx.form("email").value();
            String checkUser = "SELECT Count(id) FROM DigiData.user WHERE email_address = "+email;
            ResultSet check = Database.query(checkUser);
            check.next();
            Integer checkBool = check.getInt(1);
            if(checkBool == 0) {
                String query = "INSERT INTO DigiData.user (name, password_hash, email_address, role) VALUES ";
                query += "(" + name + ", " + hash + ", " + email + ", 'voter')";
                ctx.setResponseType(MediaType.json);
                // gather results from the database
                int results = Database.statement(query);
                // return the results as json for easy processing on the frontend
                String findNewUser = "SELECT id FROM DigiData.user WHERE email_address = " + email;
                ResultSet findID = Database.query(findNewUser);
                findID.next();
                String userID = findID.getString(1);
                String addGroup = "INSERT INTO DigiData.user_group (user_id, group_name) VALUES (" + userID + ", 'Anyone')";
                int group = Database.statement(addGroup);
                return results;
            }
            return "{\"error\": \"User already exists\"}";
        });

        app.post("/loginUser", ctx -> {
            String email = ctx.form("email").value();
            String hash = ctx.form("hash").value();

            String query = "SELECT id,name,role FROM DigiData.user WHERE email_address = \"" + email + "\" AND password_hash = \"" + hash +"\"";
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query(query);

            //Auto generate certificate key for logging in
            //TODO Ensure certificate is unique
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
            StringBuilder cert = new StringBuilder();
            Random rnd = new Random();
            while (cert.length() <= 128) { // length of the random string.
                int index = (int) (rnd.nextFloat() * chars.length());
                cert.append(chars.charAt(index));
            }
            String certKey = cert.toString();

            //Appends certificate to results
            HashMap<String, Object> res = Database.getJSON(results,"results");
            res.put("certificate",certKey);

            java.sql.Date date = new java.sql.Date(Calendar.getInstance().getTime().getTime());
            results.first();
            String[] values = {""+date.getTime(),results.getString(1)};

            certificates.put(certKey,values);

            // return the results as json for easy processing on the frontend
            return JSONValue.toJSONString(res);
        });

        app.post("/checkCertificate", ctx -> {
            java.sql.Date date = new java.sql.Date(Calendar.getInstance().getTime().getTime());
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
            return "{\"valid\": \"true\"}";
        });

        app.post("/persistElection", ctx -> {
            if(!checkCertificate(ctx)){
                return "{\"valid\": \"false\"}";
            }
            // grab all the election information from the form fields
            String uid = ctx.form("uid").value();
            String group = ctx.form("group").value();
            String start = ctx.form("startDate").value();
            String end = ctx.form("endDate").value();
            String name = ctx.form("electionName").value();
            String published = ctx.form("published").value();

            // construct the election creation statement
            String queryE = "INSERT INTO DigiData.election (user_id, group_name, start_date, end_date, name, published) VALUES";

            // append the values from the form
            queryE += " (" + uid + ", \"" + group + "\", \"" + start + "\", \"" + end + "\", \"" + name + "\", " + published + ")\n";

            // execute the statement to create the election
            Database.statement(queryE);

            // get the newly created election ID
            ResultSet rs = Database.query("SELECT id FROM DigiData.election WHERE name = \"" + name + "\" AND start_date = \"" + start + "\" AND end_date = \"" + end + "\" AND group_name = \"" + group + "\"");
            rs.next();
            int eid = rs.getInt(1);

            // insert questions and options
            String numQuestions = ctx.form("numQuestions").value();
            int num = Integer.valueOf(numQuestions);
            // for each question, insert that question and insert all corresponding options into the database
            for(int i = 1;i<=num;i++) {
                // insert question question
                String questionName = ctx.form("Q" + i).value();
                String questionType = ctx.form("Q" + i + "type").value();
                String insertQuestionStatement =  "INSERT INTO DigiData.question (election_id, name, type) VALUES( " + eid + ", \"" + questionName + "\", " + questionType + ")";
                Database.statement(insertQuestionStatement);

                // get question ID from database
                ResultSet getQueryNum = Database.query("SELECT LAST_INSERT_ID()");
                getQueryNum.next();
                int queryNum = getQueryNum.getInt(1);

                // insert all options in one statement
                int numOptions = Integer.valueOf(ctx.form("Q" + i + "numOptions").value());
                String insertOptionsStatement = "INSERT INTO DigiData.option (question_id, name) VALUES";
                for (int j = 1; j <= numOptions; j++) {
                    String optionName = ctx.form("Q" + i + "O" + j).value();
                    insertOptionsStatement += "( " + queryNum + ", " + "\"" + optionName + "\"" + ")";
                    if (j != numOptions) insertOptionsStatement += ", ";
                    else{
                        insertOptionsStatement += ";";
                    }
                }
                Database.statement(insertOptionsStatement);

            }
            // return the ID of the election created
            return "{\"electionID\": \"" + eid + "\"}";
        });

        // start the server
        app.start();
    }
    private static boolean checkCertificate(Context ctx) {
        String certificate = ctx.form("certificate").value();
        String userID = ctx.form("userID").value();
        java.sql.Date date = new java.sql.Date(Calendar.getInstance().getTime().getTime());
        String[] certPair = certificates.get(certificate);
        if(certPair != null && certPair[1].equals(userID)){
            if(date.getTime()-Long.parseLong(certPair[0])>=3600000) {
                certificates.remove(certificate);
                return false;
            }
            certPair[0]=""+date.getTime();
            certificates.replace(certificate,certPair);
            return true;
        }
        return false;
    }
}
