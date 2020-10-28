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

        app.post("/getLeaderboard", (ctx) -> {
            Database.getConnection();
            String gameType = ctx.form("gameType").value();
            ctx.setResponseType(MediaType.json);
            ResultSet results = Database.query("Select u.real_name, s.score FROM users u, Scores s, Game_Results g WHERE u.user_id = s.user_id && s.game_id = g.game_id && g.game_name = 'BlackJack'");
            return Database.getJSONFromResultSet(results, "results");
        });

        Hashtable<String,GameContainer> gameContainers = new Hashtable<String,GameContainer>();

        app.post("/hostGame", ctx -> {
            String gameID = ctx.form("gameID").value();
            String gameType = ctx.form("gameType").value();
            gameContainers.put(gameID,new GameContainer(gameID,gameType));
            System.out.println(gameID);
            switch(gameType){
                case "blackjack":
                    return "{\"blackjack\":\"Initialized\"}";
                case "solitaire":
                    return "{\"solitaire\":\"Initialized\"}";
                default:
                    return "{\"invalidGameTypeError\":\"" +gameType+"\"}";
            }
        });
        app.post("/blackjack", ctx ->{
            String gameID = ctx.form("gameID").value();
            String method = ctx.form("method").value();
            GameContainer gc = gameContainers.get(gameID);
            CardContainer deck = gc.cardContainers.get("deck");
            CardContainer discard = gc.cardContainers.get("discard");
            Boolean wasShuffled = false;
            String hand = "";
            int numberPlayers=0;
            switch(method){
                case "setup":
                    int numPlayers = Integer.parseInt(ctx.form("numPlayers").value());
                    gc.cardContainers.put("dealer",new CardContainer(0));
                    for(int i = 0;i<numPlayers;i++) {
                        gc.cardContainers.put("player" + (i + 1), new CardContainer(0));
                    }
                    numberPlayers=numPlayers+1;
                    return "{\"Shuffled\":\"false\"}";
                case "deal":
                   Set<String> ks = gc.cardContainers.keySet();
                   for(String key:ks){
                       if(!key.equals("deck") && !key.equals("discard")) {
                           CardContainer playerHand = gc.cardContainers.get(key);
                           if(!playerHand.cards.isEmpty()) {
                               for(Card card:playerHand.cards) {
                                   discard.add(card);
                                   playerHand.cards.remove(card);
                               }
                           }
                           if(deck.cards.size()<2){
                               for(Card card:discard.cards){
                                   deck.add(card);
                                   discard.remove(card);
                               }
                               deck.shuffle();
                               wasShuffled=true;
                           }
                           playerHand.cards.add(deck.cards.get(0));
                           deck.cards.remove(0);
                           playerHand.cards.add(deck.cards.get(0));
                           playerHand.cards.get(0).isFaceUp=true;
                           deck.cards.remove(0);
                       }
                   }
                   return "{\"Shuffled\":\""+wasShuffled+"\"}";
                case "hit":
                    hand = ctx.form("hand").value();
                    if(gc.cardContainers.get("deck").cards.isEmpty()){
                        for(Card card:discard.cards){
                            card.isFaceUp=true;
                            deck.add(card);
                            discard.remove(card);
                        }
                        deck.shuffle();
                        wasShuffled=true;
                    }
                    gc.cardContainers.get(hand).add(deck.cards.get(0));
                    deck.cards.remove(0);
                    return "{\"Shuffled\":\""+wasShuffled+"\"}";
                case "stay":
                    hand = ctx.form("hand").value();
                    gc.cardContainers.get(hand).isTurn=false;
                    Set<String> keyset = gc.cardContainers.keySet();
                    String[] keyArr = (String[])keyset.toArray();
                    int findIndex=0;
                    while(findIndex!=keyArr.length){
                        if(keyArr[findIndex].equals(hand)){
                            findIndex++;
                            break;
                        }
                        findIndex++;
                    }
                    gc.cardContainers.get(keyArr[findIndex]).isTurn=true;
                    break;
                case "getHand":
                    return gc.cardContainers.get(ctx.form("hand").value()).toJSON();
                case "checkIfTurn":
                    hand=ctx.form("hand").value();
                    if(gc.cardContainers.get(hand).isTurn){
                        return "{\"isTurn\":\"true\"";
                    }
                    return "{\"isTurn\":\"false\"";
               default:
                   break;
           }
            return "{\"Blackjack\":\"FinishedMethod\"}";
        });

        // start the server
        app.start();
    }
    static void blackjackNextTurn(String id){

    }
}



class Card{
    int card;
    boolean isFaceUp;
    Card(int cardNumber){
        card=cardNumber;
        isFaceUp=false;
    }
    Map<String,Object> toMap(){
        Map<String,Object> data = new HashMap<String,Object>();
        data.put("cardNum",card);
        data.put("isFaceUp",isFaceUp);
        return data;
    }
}

class CardContainer{
    ArrayList<Card> cards;
    boolean isTurn;
    void shuffle(){
        Collections.shuffle(cards);
    }
    boolean add(Card card) {
        cards.add(card);
        return true;
    }
    boolean remove(Card card){
        cards.remove(card);
        return true;
    }
    CardContainer(int numCards){
        cards = new ArrayList<Card>();
        for(int i = 1;i<=numCards;i++){
            cards.add(new Card(i));
        }
        shuffle();
        isTurn=false;
    }
    String toJSON(){
        Map<String,Object> data = new HashMap<String,Object>();
        for(int i = 0;i<cards.size();i++){
            data.put(""+i,cards.get(i).toMap());
        }
        JSONObject json = new JSONObject();
        json.putAll(data);
        return json.toJSONString();
    }
}


class GameContainer{
    Hashtable<String,CardContainer> cardContainers;
    private String ID;
    private String gameType;

    boolean addContainer(String name, int numCards){
       cardContainers.put(name,new CardContainer(numCards));
       return true;
    }
    GameContainer(String gameID, String type){
        ID=gameID;
        gameType=type;
        cardContainers = new Hashtable<String,CardContainer>();
        cardContainers.put("deck",new CardContainer(52));
        cardContainers.get("deck").shuffle();
        cardContainers.put("discard",new CardContainer(0));
    }
}