package app;

import io.jooby.*;
import org.json.simple.JSONObject;
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
    private static HashMap<String, String[]> certificates = new HashMap<String, String[]>();

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

        //sends query to mySql server to get leaderboard
        app.post("/getLeaderboard", (ctx) -> {
            Database.getConnection();
            String gameType = ctx.form("gameType").value();
            ctx.setResponseType(MediaType.json);
            ResultSet results = Database.query("Select u.real_name, s.score FROM users u, Scores s, Game_Results g WHERE u.user_id = s.user_id && s.game_id = g.game_id && g.game_name = '"+ gameType + "' ORDER BY score DESC");
            return Database.getJSONFromResultSet(results, "results");
        });

        Hashtable<String, GameContainer> gameContainers = new Hashtable<String, GameContainer>();

        app.post("/hostGame", ctx -> {
            String gameID = "" + (gameContainers.isEmpty()?1:(gameContainers.keySet().size() + 1));
            String gameType = ctx.form("gameType").value();
            gameContainers.put(gameID, new GameContainer(gameID, gameType));
            switch (gameType) {
                case "blackjack":
                    return "{\"blackjack\":\""+gameID+"\"}";
                case "solitaire":
                    return "{\"solitaire\":\""+gameID+"\"}";
                default:
                    return "{\"invalidGameTypeError\":\"" + gameType + "\"}";
            }
        });

        app.post("/joinGame",ctx -> {
            String gameID = ctx.form("gameID").value();
            gameContainers.get(gameID).numPlayers+=1;
            String playerID = "player" + (gameContainers.get(gameID).numPlayers);
            gameContainers.get(gameID).addContainer(playerID,0);
            return "{\"playerID\":\""+playerID+"\"}";
        });

        app.post("/loginUser", ctx -> {
            String email = ctx.form("email").value();
            String hash = ctx.form("hash").value();

            String query = "SELECT user_id,real_name,username FROM users WHERE email = \"" + email + "\" AND password_hash = \"" + hash +"\"";
            ctx.setResponseType(MediaType.json);

            // gather results from the database
            ResultSet results = Database.query(query);

            //Auto generate certificate key for logging in
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

        app.post("/persistInsertUser", ctx -> {
            String username = ctx.form("username").value();
            String name = ctx.form("real_name").value();
            String hash = ctx.form("hash").value();
            String email = ctx.form("email").value();
            String checkUser = "SELECT Count(user_id) FROM users WHERE email = "+email;
            ResultSet check = Database.query(checkUser);
            check.next();
            Integer checkBool = check.getInt(1);
            if(checkBool == 0) {
                String query = "INSERT INTO users (real_name, username, password_hash, email) VALUES ";
                query += "(" + name + ", " + username + "," + hash + ", " + email + ")";
                ctx.setResponseType(MediaType.json);
                // gather results from the database
                int results = Database.statement(query);
                // return the results as json for easy processing on the frontend
                String findNewUser = "SELECT user_id FROM users WHERE email = " + email;
                ResultSet findID = Database.query(findNewUser);
                findID.next();
                return results;
            }
            return "{\"error\": \"User already exists\"}";
        });

        app.post("/getGames",ctx->{
            String gameType = ctx.form("gameType").value();
            String jsonStr="{";
            for(String game:gameContainers.keySet()){
                if(gameContainers.get(game).getType().equals(gameType)) {
                    jsonStr += "\"" + gameContainers.get(game).getID() + "\":\"" + gameContainers.get(game).numPlayers + "\",";
                }
            }
            if(jsonStr.length()==1){
                return "{\"NoGamesAvailable\":\"true\"}";
            }
            jsonStr = jsonStr.substring(0,jsonStr.length()-1) + "}";
            System.out.println(jsonStr);
            return jsonStr;
        });

        app.post("/blackjack", ctx -> {
            String gameID = ctx.form("gameID").value();
            String method = ctx.form("method").value();
            GameContainer gc = gameContainers.get(gameID);
            CardContainer deck = gc.cardContainers.get("deck");
            CardContainer discard = gc.cardContainers.get("discard");
            Boolean wasShuffled = false;
            String hand = "";
            Set<String> keyset;
            String[] keyArr;
            ArrayList<String> handOrder=new ArrayList<String>();
            Card card;
//            int numberPlayers = 0;
            switch (method) {
                case "setup":
//                    int numPlayers = gc.cardContainers.keySet().size();
//                    for (int i = 0; i < numPlayers; i++) {
//                        gc.cardContainers.put("player" + (i + 1), new CardContainer(0));
//                    }
                    System.out.println(gc.cardContainers.keySet());
                    gc.cardContainers.get("player1").isTurn = true;
                    gc.cardContainers.put("dealer", new CardContainer(0));
                    return "{\"Shuffled\":\"false\"}";
                case "deal":
                    if(!gc.roundActive) {
                        gc.roundActive = true;
                        Set<String> ks = gc.cardContainers.keySet();

                        keyArr = ks.toArray(new String[0]);
                        for(String key: keyArr){
                            System.out.println(key);
                            System.out.println(key.substring(0,1));
                            if(key.substring(0,1).equals("p")){
                                handOrder.add(key);
                            }
                        }
                        gc.cardContainers.get(handOrder.get(0)).isTurn=true;
//                        gc.cardContainers.get("player1").isTurn=true;
                        for (String key : ks) {
                            if (!key.equals("deck") && !key.equals("discard")) {
                                CardContainer playerHand = gc.cardContainers.get(key);
                                if (!playerHand.cards.isEmpty()) {
                                    for (Card cd : playerHand.cards) {
                                        discard.add(cd);
                                    }
                                    playerHand.cards.clear();
                                }
                                if (deck.cards.size() < 2) {
                                    for (Card cd : discard.cards) {
                                        deck.add(cd);
                                    }
                                    discard.cards.clear();
                                    deck.shuffle();
                                    wasShuffled = true;
                                }
                                playerHand.cards.add(deck.cards.get(0));
                                deck.cards.remove(0);
                                playerHand.cards.add(deck.cards.get(0));
                                playerHand.cards.get(1).isFaceUp = true;
                                deck.cards.remove(0);
                            }
                        }
                        return "{\"Shuffled\":\"" + wasShuffled + "\"}";
                    }
                    return "{\"AlreadyDealt\":\"true\"}";
                case "hit":
                    hand = ctx.form("hand").value();
                    if(gc.cardContainers.get(hand).isTurn) {
                        if (gc.cardContainers.get("deck").cards.isEmpty()) {
                            for (Card cd : discard.cards) {
                                deck.add(cd);
                                discard.remove(cd);
                            }
                            deck.shuffle();
                            wasShuffled = true;
                        }
                        card = deck.cards.get(0);
                        card.isFaceUp = true;
                        gc.cardContainers.get(hand).add(card);
                        deck.cards.remove(0);
                        return "{\"Shuffled\":\"" + wasShuffled + "\"}";
                    }
                    return "{\"Error\":\"NotYourTurn\"}";
                case "stay":
                    hand = ctx.form("hand").value();
                    Set<String> ks = gc.cardContainers.keySet();
                    keyArr = ks.toArray(new String[0]);
                    for(String key: keyArr){
                        System.out.println(key);
                        System.out.println(key.substring(0,1));
                        if(key.substring(0,1).equals("p")){
                            handOrder.add(key);
                        }
                    }

                    if(gc.cardContainers.get(hand).isTurn) {
                        gc.cardContainers.get(hand).isTurn = false;
//                        keyset = gc.cardContainers.keySet();
//                        keyArr = keyset.toArray(new String[0]);
                        int index = handOrder.indexOf(hand)+1;
                        if(index>=handOrder.size()){
                            gc.cardContainers.get("dealer").isTurn=true;
                        }
                        else{
                            gc.cardContainers.get(handOrder.get(index));
                        }
//                        int findIndex = 2; //Starts at 2 to skip deck and discard hands
//                        while (findIndex != keyArr.length) {
//                            if (keyArr[findIndex].equals(hand)) {
//                                findIndex++;
//                                break;
//                            }
//                            findIndex++;
//                        }
//                        if (findIndex >= keyArr.length) {
//                            findIndex -= keyArr.length; //Circle back around to the first hand
//                            findIndex += 2; //skip deck and discard
//                        }
//                        gc.cardContainers.get(keyArr[findIndex]).isTurn = true;
                        break;
                    }
                    return "{\"Error\":\"NotYourTurn\"}";
                case "getHand":
                    return gc.cardContainers.get(ctx.form("hand").value()).toJSON(false);
                case "checkIfTurn":
                    hand = ctx.form("hand").value();
                    if (gc.cardContainers.get(hand).isTurn) {
                        return "{\"isTurn\":\"true\"}";
                    }
                    if (hand.equals("player1") && gc.cardContainers.get("dealer").isTurn) {
                        CardContainer dealer = gc.cardContainers.get("dealer");
                        int score = 0, aceCount = 0;
                        //Put dealer logic here
                        dealer.cards.get(0).isFaceUp = true;
                        for (int i = 0; i < dealer.cards.size(); ++i) {
                            int val = dealer.getValue(i, "blackjack");
                            if (val == 1) {
                                aceCount++;
                                score += 11;
                            } else
                                score += val;
                        }
                        while (score <= 16) {
                            if (gc.cardContainers.get("deck").cards.isEmpty()) {
                                for (Card card1 : discard.cards) {
                                    deck.add(card1);
                                    discard.remove(card1);
                                }
                                deck.shuffle();
                                wasShuffled = true;
                            }
                            card = deck.cards.get(0);
                            card.isFaceUp = true;
                            dealer.add(card);
                            deck.cards.remove(0);
                            int val = dealer.getValue(dealer.cards.indexOf(card), "blackjack");
                            if (val == 1) {
                                aceCount++;
                                score += 11;
                            } else
                                score += val;

                            while (aceCount != 0) {
                                if (score > 21) {
                                    score -= 10;
                                    aceCount -= 1;
                                } else
                                    break;
                            }
                        }
                        gc.roundActive = false;
                        gc.cardContainers.get("dealer").isTurn=false;
                        keyset = gc.cardContainers.keySet();
                        for (String key : keyset) {
                            if (!key.equals("deck") && !key.equals("discard")) {
                                CardContainer playerHand = gc.cardContainers.get(key);
                                for (Card cd : playerHand.cards) {
                                    cd.isFaceUp = true;
                                }
                            }
                        }
                    }
                    if (!gc.roundActive) {
                        int dealerScore = gc.cardContainers.get("dealer").addCards("blackjack");
                        int playerScore = gc.cardContainers.get(hand).addCards("blackjack");
                        System.out.println("Player "+playerScore+" Dealer "+dealerScore);

                        if(playerScore>21){
                            return "{\"WinOrLose\":\"Lose\",\"PlayerScore\":\""+playerScore+"\",\"DealerScore\":\""+dealerScore+"\"}";
                        }
                        else if(dealerScore>21){
                            return "{\"WinOrLose\":\"Win\",\"PlayerScore\":\""+playerScore+"\",\"DealerScore\":\""+dealerScore+"\"}";
                        }
                        else if(dealerScore > playerScore){
                            return "{\"WinOrLose\":\"Lose\",\"PlayerScore\":\""+playerScore+"\",\"DealerScore\":\""+dealerScore+"\"}";
                        }
                        else if(playerScore > dealerScore){
                            return "{\"WinOrLose\":\"Win\",\"PlayerScore\":\""+playerScore+"\",\"DealerScore\":\""+dealerScore+"\"}";
                        }
                        else{
                            return "{\"WinOrLose\":\"Tie\",\"PlayerScore\":\""+playerScore+"\",\"DealerScore\":\""+dealerScore+"\"}";
                        }
                    }
                    return "{\"isTurn\":\"false\"}";
                case "showCards":
                    keyset = gc.cardContainers.keySet();
                    keyArr = keyset.toArray(new String[0]);
                    String json = "{";
                    for (int i = 2; i < keyArr.length - 1; i++) {
                        json += "\"" + keyArr[i] + "\"" + ":" + (gc.cardContainers.get(keyArr[i]).toJSON(true)) + ",";
                    }
                    json += "\"" + keyArr[keyArr.length - 1] + "\"" + ":" + (gc.cardContainers.get(keyArr[keyArr.length - 1]).toJSON(true)) + "}";
                    return json;
                default:
                    break;
            }
            return "{\"Shuffled\":\"false\"}";
        });

        // start the server
        app.start();
    }
}
/** Card object to hold data of each individual card
 * The integer value will be between 0-51 to represent one of the 52 cards in a deck
 * The boolean value determines if the card will be displayed face-up or face-down **/
class Card {
    int card;
    boolean isFaceUp;

    /** By default the card will be created face-down **/
    Card(int cardNumber) {
        card = cardNumber;
        isFaceUp = false;
    }

    /** Changes card to a map to make it easier to convert to json**/
    Map<String, Object> toMap(boolean pub) {
        Map<String, Object> data = new HashMap<String, Object>();
        if (pub && !isFaceUp) {
            data.put("cardNum", -1);
        } else {
            data.put("cardNum", card);
        }
        data.put("isFaceUp", isFaceUp);
        return data;
    }
}

/** This object is a container that will hold multiple card objects. In a game it will be
 * used for the deck, players hands, a middle pile, ect.
 * It has an arraylist to hold the card objects.
 * It has a boolean, "isTurn" to be used for player hands to determine who's turn it is **/
class CardContainer {
    ArrayList<Card> cards;
    boolean isTurn;

    /** A function that randomly shuffles the card container **/
    void shuffle() {
        Collections.shuffle(cards);
    }

    /** A function that adds a card to the container **/
    boolean add(Card card) {
        cards.add(card);
        return true;
    }

    /** A function that adds a card to the container **/
    boolean remove(Card card) {
        cards.remove(card);
        return true;
    }

    /** Constructor, It creates an arraylist and populates it with the number of cards passed to it.
     * Normally this would be used to create a standard 52 card deck at the start of a game but could have other uses. **/
    CardContainer(int numCards) {
        cards = new ArrayList<Card>();
        for (int i = 0; i < numCards; i++) {
            cards.add(new Card(i));
        }
        shuffle();
        isTurn = false;
    }

    /** A function to get the score of players hand. Different games will have different scoring so the there is a
     * switch statement to determine the scoring needed for each game **/
    int addCards(String gameType) {
        switch (gameType) {
            case "blackjack":
                int score = 0,aceCount=0;
                for (int i = 0; i < cards.size(); ++i) {
                    int val = getValue(i, gameType);
                    if (val == 1) {
                        aceCount++;
                        score += 11;
                    } else
                        score += val;
                }
                while (aceCount != 0) {
                    if (score > 21) {
                        score -= 10;
                        aceCount -= 1;
                    } else
                        break;
                }
                return score;
            default:
                return 0;
        }
    }

    /** A function used by addCards to determine the value of an individual card, also based on a game type **/
    int getValue(int cardNum, String gameType) {
        int cardValue = 0;
        switch (gameType) {
            case "blackjack":
                cardValue = (cards.get(cardNum).card % 13);
                System.out.println(cardValue);
                if (cardValue > 9)
                    return 10;
                else
                    return cardValue+1;
            default:
                return 0;
        }
    }

    /** Converts the arraylist to json so it can be sent to the frontend web application **/
    String toJSON(boolean pub) {
        Map<String, Object> data = new HashMap<String, Object>();
        for (int i = 0; i < cards.size(); i++) {
            data.put("" + i, cards.get(i).toMap(pub));
        }
        JSONObject json = new JSONObject();
        json.putAll(data);
        return json.toJSONString();
    }
}

/** This is a game container object to hold multiple card containers
 * It consists of a hashtable to store the card containers,
 * a string ID to identify each game,
 * a string gameType to distinguish the type of game, ie Blackjack,
 * and a boolean variable to tell if the game is still active**/
class GameContainer {
    Hashtable<String, CardContainer> cardContainers;
    private String ID;
    private String gameType;
    public int numPlayers;
    public boolean roundActive;

    /** This adds a card container to the hashtable **/
    boolean addContainer(String name, int numCards) {
        cardContainers.put(name, new CardContainer(numCards));
        return true;
    }

    String getType(){return gameType;}

    String getID(){return ID;}
    /** Constructor, by default it sets the roundActive to true and creates a 'deck' with 52 cards and shuffles them **/
    GameContainer(String gameID, String type) {
        ID = gameID;
        gameType = type;
        numPlayers = 0;
        roundActive = false;
        cardContainers = new Hashtable<String, CardContainer>();
        cardContainers.put("deck", new CardContainer(52));
        cardContainers.get("deck").shuffle();
        cardContainers.put("discard", new CardContainer(0));
    }
}
