package com.beanchainbeta.TXs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.fasterxml.jackson.databind.ObjectMapper;

public class InternalTX extends TX {
    private String from;
    //private int nonce;
    private String publicKeyHex;
    private String to;
    private double amount;
    private long timeStamp;
    private String txhash;
    private String signature;

    

    public InternalTX(){

    }

    public InternalTX(String from, String publicKeyHex, String to, double amount) {
        this.from = from;
        this.publicKeyHex = publicKeyHex;
        this.to = to;
        this.amount = amount;
        this.timeStamp = System.currentTimeMillis();
        this.txhash = generateHash();
    }

    public String getFrom() {return from;}
    public String getPublicKeyHex() {return publicKeyHex;}
    public String getTo() {return to;}
    public double getAmount() {return amount;}
    public String getTxHash() {return txhash;}
    public String getSignature() {return signature;}

    public void setFrom(String from) {this.from = from;}
    public void setPublicKeyHex(String publicKeyHex) {this.publicKeyHex = publicKeyHex;}
    public void setTo(String to) {this.to = to;}
    public void setAmount(double amount) {this.amount = amount;}
    public void setTimeStamp(long timeStamp) {this.timeStamp = timeStamp;}
    public void setTxHash(String txhash) {this.txhash = txhash;}
    public void setSignature(String signature) {this.signature = signature;}

    private String generateHash(){
        try {
            String data = from + to + amount + timeStamp;
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

    public static TX fromJSON(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, InternalTX.class);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    
}
