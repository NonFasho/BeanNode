package com.beanchainbeta.services;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.springframework.stereotype.Service;
import com.beanchainbeta.TXs.TX;
import static org.iq80.leveldb.impl.Iq80DBFactory.*;

@Service
public class MempoolService {
    private static ConcurrentHashMap<String, String> transactions = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, TX> rejectedTransactions = new ConcurrentHashMap<>();
    private static DB db;
    
    public MempoolService(){
        try {
            Options options = new Options();
            options.createIfMissing(true);
            db = factory.open(new File("mempool_db"), options);
            loadMempoolFromDB();
        } catch (Exception e) {
            throw new RuntimeException("Error initializing LevelDB", e);
        }
    
    }
    
    public static boolean addTransaction(String txHash, String transactionJson) {
        if(!transactions.containsKey(txHash)) {
            transactions.put(txHash, transactionJson);
            try {
                db.put(bytes(txHash), bytes(transactionJson));
            } catch (Exception e) {
                System.err.println("Error saving transaction to DB: " + e.getMessage());
            }
            return true;
        }
            return false;
        }

        public static void removeTXs(ArrayList<TX> acceptedTxs, ConcurrentHashMap<String, TX> rejectedTxs) {
            for (TX tx : acceptedTxs) {
                String txHash = tx.getTxHash();
                if (transactions.containsKey(txHash)) {
                    transactions.remove(txHash);
                    try {
                        db.delete(bytes(txHash));
                    } catch (Exception e) {
                        System.err.println("Error deleting accepted TX from DB: " + e.getMessage());
                    }
                } else {
                    //System.out.println(txHash + ": not found in mempool");
                }
            }
        
            for (String txHash : rejectedTxs.keySet()) {
                if (transactions.containsKey(txHash)) {
                    transactions.remove(txHash);
                    try {
                        db.delete(bytes(txHash));
                    } catch (Exception e) {
                        System.err.println("Error deleting rejected TX from DB: " + e.getMessage());
                    }
                } else {
                    System.out.println(txHash + ": not found in mempool");
                }
            }
        }
        
    
    public ConcurrentHashMap<String, String> getTransactions() {
        return transactions;
    }

    public static ConcurrentHashMap<String, String> getRejectedTransactions(String address) {
        ConcurrentHashMap<String, String> result = new ConcurrentHashMap<>();

        //** TEST TEST TEST */
        //System.out.println("Looking for rejected TXs from: " + address);
        //** TEST END TEST END */
    
        for (Map.Entry<String, TX> entry : rejectedTransactions.entrySet()) {
            TX tx = entry.getValue();
            if (tx.getFrom().equals(address)) {

                //System.out.println("Comparing: " + tx.getFrom() + " vs. " + address);


                //System.out.println("   ✔ Match: " + tx.getTxHash());

                result.put(entry.getKey(), tx.createJSON());
            }
        }

        return result;
    }
    

    
    private void loadMempoolFromDB() {
        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                java.util.Map.Entry<byte[], byte[]> entry = iterator.next();
                String txHash = new String(entry.getKey(), StandardCharsets.UTF_8);
                String transactionJson = new String(entry.getValue(), StandardCharsets.UTF_8);
                transactions.put(txHash, transactionJson);
            }
            System.out.println("Mempool restored from LevelDB.");
        } catch (Exception e) {
            System.err.println("Error loading mempool from DB: " + e.getMessage());
        }
    }
    
    public static ArrayList<TX> getTxFromPool(){
        ArrayList<TX> txList = new ArrayList<>();
        for( String d: transactions.values()) {
        txList.add(TX.fromJSON(d));
        //System.out.println(TX.fromJSON(d));
    }
    return txList; 
    }

    public static ArrayList<TX> getPending(String addy) {
        ArrayList<TX> pendingTX = new ArrayList<>();
    
        for (String d : transactions.values()) {
            TX tx = TX.fromJSON(d);
            if (tx != null && tx.getFrom().equals(addy)) {
                pendingTX.add(tx);
            }
        }
    
        return pendingTX;
    }
    

    

}

