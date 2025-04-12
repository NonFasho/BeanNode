package com.beanchainbeta.TXs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
//import com.beanchainbeta.nodePixs.StateWallet;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GenesisTX extends TX{
    private String from;
    private String to;
    private double amount;
    private String txhash;
    private String signature;
    private long timeStamp = 0L; // 🔒 Hardcoded for deterministic hash
    private int nonce = 0;

    public String getFrom() {return from;}
    public String getTo() {return to;}
    public double getAmount() {return amount;}
    public String getTxHash() {return txhash;}
    public String getSignature() {return signature;}
    public long getTimeStamp() { return timeStamp; }
    public int getNonce() { return nonce; }

    public GenesisTX() {

    }

    public GenesisTX(String to, double amount) {
        from = "BEANX:00000000000000000000";
        this.to = to;
        this.amount = amount;
        txhash = generateHash();
        signature = "GENESIS-SIGNATURE";

    }

    private String generateHash(){
        try {
            String data = from + to + amount;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for(byte b: hash){
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String createJSON() {
        String jsonString = "";
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            jsonString = objectMapper.writeValueAsString(this);
        } catch (Exception e) {
            System.out.println(e);
        }
        return jsonString;
    }

    // public void updateStateIncoming(){
    //     StateWallet update = new StateWallet(this.to, this.amount);
        
    // }

    

}
