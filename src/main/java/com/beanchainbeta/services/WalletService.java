package com.beanchainbeta.services;

import java.io.IOException;
import org.iq80.leveldb.DB;
import org.springframework.stereotype.Service;
import com.beanchainbeta.TXs.TX;
import com.beanchainbeta.controllers.DBManager;
import com.beanchainbeta.tools.StateWallet;
import com.beanchainbeta.tools.beantoshinomics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.iq80.leveldb.DBIterator;
import static org.iq80.leveldb.impl.Iq80DBFactory.*;

@Service
public class WalletService {
    private static DB db = DBManager.getDB("stateDB");
    

    //dev testing
    

    public static void genBlock() throws IOException{
        String teamWallet = "BEANX:0x1c8496175b3f4802e395db5fab4dd66e09c431b2";
        double teamAllocation = 2500000;
        StateWallet team = new StateWallet();
        InBeanTx(teamWallet, teamAllocation);
        

        String earlyWallet = "BEANX:0xEARLYWALLET";
        double earleyWaletAllocation = 5000000;
        StateWallet early = new StateWallet();
        early.setAddy(earlyWallet);
        early.setBeantoshi(beantoshinomics.toBeantoshi(earleyWaletAllocation));
        InBeanTx(earlyWallet, earleyWaletAllocation);

        System.out.println(getData(earlyWallet));
        System.out.println(getData(teamWallet));
    }

    public static void genTxProcess(TX tx) throws IOException{
        if(tx.getSignature().equals("GENESIS-SIGNATURE")) {
            String toKey = tx.getTo();
            double amount = tx.getAmount();
            InBeanTx(toKey, amount);   
            outBeanTx(tx.getFrom(), amount, tx.getNonce());

        }
    }


    //end dev testing wallet storage 

    // update sender wallet state 
    private static void outBeanTx(String from, double amount, int nonce) throws IOException{
        ObjectMapper mapper = new ObjectMapper();
        String fromKey = from;
        long beantoshi = beantoshinomics.toBeantoshi(amount);

        byte[] fromJsonWallet = db.get(fromKey.getBytes(StandardCharsets.UTF_8));

        //update sender wallet
        if(fromJsonWallet != null) {
            ObjectNode walletNode = (ObjectNode) mapper.readTree(new String(fromJsonWallet, StandardCharsets.UTF_8));
            long currentBeantoshi = walletNode.get("beantoshi").asLong();
            long newBalance = currentBeantoshi - beantoshi;
            walletNode.put("beantoshi", newBalance);
            walletNode.put("nonce", (nonce + 1));

            String updatedSender = mapper.writeValueAsString(walletNode);
            db.put(fromKey.getBytes(StandardCharsets.UTF_8), updatedSender.getBytes(StandardCharsets.UTF_8)); 
        } else {
            System.out.println("No Wallet Found");
        }
    }

    // update reciever wallet state 
    private static void InBeanTx(String to, double amount) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String toKey = to;
        long beantoshi = beantoshinomics.toBeantoshi(amount);
    
        byte[] toJsonWallet = db.get(toKey.getBytes(StandardCharsets.UTF_8));
        ObjectNode walletNode;
    
        if (toJsonWallet != null) {
    
            try {
                walletNode = (ObjectNode) mapper.readTree(new String(toJsonWallet, StandardCharsets.UTF_8));
                long currentBeantoshi = walletNode.get("beantoshi").asLong();
                walletNode.put("beantoshi", currentBeantoshi + beantoshi);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error parsing JSON from DB!");
                return;
            }
        } else {
            System.out.println("No Wallet Found for: " + to + ", creating new one.");
            walletNode = mapper.createObjectNode();
            if(getBeanBalance("BEANX:0xEARLYWALLET") > 100){
                TX tx = new TX("BEANX:0xEARLYWALLET","SYSTEM", to, 100 ,getNonce("BEANX:0xEARLYWALLET"));
                tx.setSignature("GENESIS-SIGNATURE");
                MempoolService.addTransaction(tx.getTxHash(), tx.createJSON());
            }
            walletNode.put("beantoshi", beantoshi);
            walletNode.put("nonce", 0);
        }
    
        // Try serializing JSON
        String updatedSender = "";
        try {
            updatedSender = mapper.writeValueAsString(walletNode);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error serializing JSON!");
            return;
        }
    
        System.out.println("Updated Wallet JSON: " + updatedSender);
    
        // Ensure JSON is not empty before saving
        if (updatedSender == null || updatedSender.trim().isEmpty()) {
            System.out.println("ERROR: Updated wallet JSON is empty, NOT writing to DB!");
            return;
        }
    
        // Save the updated wallet to LevelDB
        db.put(toKey.getBytes(StandardCharsets.UTF_8), updatedSender.getBytes(StandardCharsets.UTF_8));
    
        System.out.println("Successfully updated wallet for: " + to);
    }
    
    
    //close DB
    public void closeDB() {
        try {
            db.close();
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public static String getData(String key) {
        return asString(db.get(bytes(key)));
    }

    public static double getBeanBalance(String addy){
        String json = getData(addy);
        double balance = 0;
        ObjectMapper objectMapper = new ObjectMapper();
    
        try {
            if (json == null) {
                System.err.println("⚠️ No data found for address: " + addy);
                return 0.0;
            }
    
            JsonNode rootNode = objectMapper.readTree(json);
            long beantoshiBalance = rootNode.get("beantoshi").asLong();
            balance = beantoshinomics.toBean(beantoshiBalance);
    
        } catch (Exception e) {
            e.printStackTrace();
        }
    
        return balance;
    }
    

    public static boolean hasCorrectAmount(String addy, double amount) {
        boolean test = false;
        
        System.out.println(amount + " vs " + getBeanBalance(addy));

        if(getBeanBalance(addy) >= amount){
            test = true;
        }
        return test;
    }

    public static void transfer(TX tx) throws IOException {
        outBeanTx(tx.getFrom(), tx.getAmount(), tx.getNonce());
        InBeanTx(tx.getTo(), tx.getAmount());
        
    }

    public static int getNonce(String addy){
        int nonce = 0;
        ObjectMapper mapper = new ObjectMapper();
    
        byte[] toJsonWallet = db.get(addy.getBytes(StandardCharsets.UTF_8));
        ObjectNode walletNode;
    
        if (toJsonWallet != null) {
            System.out.println("Existing wallet data found for: " + addy);
            System.out.println("Raw JSON from DB: " + new String(toJsonWallet, StandardCharsets.UTF_8));
    
            try {
                walletNode = (ObjectNode) mapper.readTree(new String(toJsonWallet, StandardCharsets.UTF_8));
                int currentNonce = walletNode.get("nonce").asInt();
                nonce = currentNonce;
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error parsing JSON from DB!");
            }
        } else {
            System.out.println("No Wallet Found for: " + addy + ".");
            
        }
        return nonce;
    }

    public static int incrementNonce(String addy) {
        int newNonce = 0;
        ObjectMapper mapper = new ObjectMapper();
    
        byte[] toJsonWallet = db.get(addy.getBytes(StandardCharsets.UTF_8));
    
        if (toJsonWallet != null) {
            try {
                ObjectNode walletNode = (ObjectNode) mapper.readTree(new String(toJsonWallet, StandardCharsets.UTF_8));
                int currentNonce = walletNode.get("nonce").asInt();
                newNonce = currentNonce + 1;
                walletNode.put("nonce", newNonce);
    
                String updatedJson = mapper.writeValueAsString(walletNode);
                db.put(addy.getBytes(StandardCharsets.UTF_8), updatedJson.getBytes(StandardCharsets.UTF_8));
    
                System.out.println("Nonce updated to: " + newNonce);
    
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error parsing or updating wallet JSON!");
            }
        } else {
            System.out.println("ERROR: Tried to increment nonce for non-existent wallet: " + addy);
            // Optional: throw exception or return -1
        }
    
        return newNonce;
    }

    

    public static List<StateWallet> getAllWallets() {
        List<StateWallet> txs = new ArrayList<>();

        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                String key = asString(entry.getKey());

                if (key.startsWith("BEANX:0x")) {
                    String json = asString(entry.getValue());
                    System.out.println(key + " " + json);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return txs;
    }



    public static void main(String[] args) throws Exception {
        WalletService service = new WalletService();

        InBeanTx("BEANX:0xf4f99e50d2f333c4b5130a0807906aaf0d512280", 500);

        //System.out.println(getBeanBalance("BEANX:0xf4f99e50d2f333c4b5130a0807906aaf0d512280"));
        getAllWallets();
        System.out.println(getNonce("BEANX:0xf4f99e50d2f333c4b5130a0807906aaf0d512280"));

        
        
        
        

    }
}

