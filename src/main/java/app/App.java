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
            switch(method){
                case "setup":
                    int numPlayers = Integer.parseInt(ctx.form("numPlayers").value());
                    gc.cardContainers.put("dealer",new CardContainer(0));
                    for(int i = 0;i<numPlayers;i++){
                        gc.cardContainers.put("player"+(i+1),new CardContainer(0));
                    }
                    return gc.cardContainers.get("deck").toJSON();
                case "deal":
                   Set<String> ks = gc.cardContainers.keySet();
                   CardContainer deck = gc.cardContainers.get("deck");
                   for(String key:ks){
                       if(key!="deck") {
                           CardContainer hand = gc.cardContainers.get(key);
                           hand.cards.add(deck.cards.get(0));
                           deck.cards.remove(0);
                           hand.cards.add(deck.cards.get(0));
                           deck.cards.remove(0);
                       }
                   }
                   return gc.cardContainers.get("deck").toJSON();
                case "hit":
                    String hand = ctx.form("hand").value();
                    gc.cardContainers.get(hand).add(gc.cardContainers.get("deck").cards.get(0));
                    gc.cardContainers.get("deck").cards.remove(0);
                    return gc.cardContainers.get(hand).toJSON();
                case "getHand":
                    return gc.cardContainers.get(ctx.form("hand").value()).toJSON();
               default:
                   break;
           }
            return "{Blackjack:FinishedMethod}";
        });

        // start the server
        app.start();
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
    String ID;
    String gameType;

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
    }
}