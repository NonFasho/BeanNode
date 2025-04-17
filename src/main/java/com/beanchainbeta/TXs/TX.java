package com.beanchainbeta.TXs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;

import com.beanchainbeta.Validation.TransactionVerifier;
import com.beanchainbeta.controllers.MessageRouter;
import com.beanchainbeta.network.Node;
import com.beanchainbeta.services.RejectedService;
import com.beanchainbeta.services.WalletService;
import com.beanchainbeta.tools.SHA256TransactionSigner;
import com.beanchainbeta.tools.WalletGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TX {
    private String from;
    private int nonce;
    private String publicKeyHex;
    private String to;
    private double amount;
    private long timeStamp;
    private String txHash;
    private String signature;
    private long gasFee;
    private String status = "pending";

    public TX(){

    }

    public TX(String from, String publicKeyHex, String to, double amount, int nonce, long gasFee) {
        this.from = from;
        this.publicKeyHex = publicKeyHex;
        this.to = to;
        this.amount = amount;
        this.timeStamp = System.currentTimeMillis();
        this.nonce = nonce;
        this.txHash = generateHash();
        this.gasFee = gasFee;
    }

    public String getFrom() {return from;}
    public int getNonce() {return nonce;}
    public String getPublicKeyHex() {return publicKeyHex;}
    public String getTo() {return to;}
    public double getAmount() {return amount;}
    public long getTimeStamp() {return timeStamp;}
    public String getTxHash() {return txHash;}
    public String getSignature() {return signature;}
    public long getGasFee() {return gasFee;}
    public String getStatus() {return status;}

    public void setFrom(String from) {this.from = from;}
    public void setNonce(int nonce) {this.nonce = nonce;}
    public void setPublicKeyHex(String publicKeyHex) {this.publicKeyHex = publicKeyHex;}
    public void setTo(String to) {this.to = to;}
    public void setAmount(double amount) {this.amount = amount;}
    public void setTimeStamp(long timeStamp) {this.timeStamp = timeStamp;}
    public void setTxHash(String txHash) {this.txHash = txHash;}
    public void setSignature(String signature) {this.signature = signature;}
    public void setGasFee(long gasFee) {this.gasFee = gasFee;}
    public void setStatus(String status) {this.status = status;}

    private String generateHash(){
        try {
            String data = from + to + String.format("%.8f", amount) + timeStamp + nonce + gasFee;
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

    //for api submitted TX taken from JSON 
    //entry for all 3rd party built bean transactions 
    public static TX fromJSON(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            TX tx = objectMapper.readValue(json, TX.class);
    
            // 🔧 Enforce 8-decimal precision on amount
            if (tx != null) {
                tx.amount = Math.round(tx.amount * 1e8) / 1e8;
            }
    
            return tx;
        } catch (Exception e) {
            System.out.println("❌ Failed to parse TX JSON: " + e.getMessage());
            return null;
        }
    }

    public void sign(PrivateKey privateKey) throws Exception{
        publicKeyHex = WalletGenerator.generatePublicKey(privateKey);
        
        byte[] transactionHash = hexToBytes(this.txHash);
        signature = SHA256TransactionSigner.signSHA256Transaction(privateKey, transactionHash);
        
    }

    public static byte[] hexToBytes(String hex) {
        int length = hex.length();
        byte[] data = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    //runs a lot of boolean checks to decide if the transaction is valid and can be added to a new block 
    public  boolean verfifyTransaction() throws Exception{
        //debug
        //this.debugHashValues();
        //end-debug
        if (this.signature != null && this.signature.equals("GENESIS-SIGNATURE")) {
            //System.out.println("🪙 System TX accepted without signature verification: " + txHash);
            return true;
        }

        boolean hasAddy = (this.from !=null);
        //System.out.println("HasAddy" + (hasAddy));
        boolean hasSignature = (this.signature !=null);
        //System.out.println("HasSignature " + (hasSignature));
        //System.out.println(this.txHash + " vs gen: " + this.generateHash());
        boolean correctHash = (this.txHash.equals(this.generateHash()));
        //System.out.println("CorrectHash " + (correctHash));
        //System.out.println("CorrectNonce " + (this.nonce == WalletService.getNonce(from)));
        boolean correctNonce = (this.nonce == (WalletService.getNonce(from)));
        boolean addyMatch = false;
        boolean validOwner = false;
        boolean senderHasEnough = false;

        if(hasAddy && hasSignature && correctHash && correctNonce) {
            addyMatch = TransactionVerifier.walletMatch(publicKeyHex, from);
            //System.out.println("addymatch: " + addyMatch);
            validOwner = TransactionVerifier.verifySHA256Transaction(publicKeyHex, hexToBytes(txHash), signature);
            //System.out.println("validowner: " + validOwner);
            senderHasEnough = WalletService.hasCorrectAmount(from, amount, gasFee);
            //System.out.println("sender has enough: " + senderHasEnough);
            if(addyMatch && validOwner && senderHasEnough) {
                return true;
            } else {
                System.out.println("** TX FAILED: " +txHash + " VERIFICATION FAILURE **");
                this.setStatus("rejected");
                RejectedService.saveRejectedTransaction(this);
                Node.broadcastRejection(txHash);
                return false;
            }

        } else {
            System.out.println("** TX FAILED: " + txHash + " INFO MISMATCH **");
            this.setStatus("rejected");
            RejectedService.saveRejectedTransaction(this);
            Node.broadcastRejection(txHash);
            return false;

        }
    }

    public boolean lightSyncVerify() throws Exception {
        if (this.signature != null && this.signature.equals("GENESIS-SIGNATURE")) {
            //System.out.println("🪙 System TX accepted without signature verification: " + txHash);
            return true;
        }
        boolean hasAddy = (this.from != null);
        boolean hasSignature = (this.signature != null);
        boolean correctHash = (this.txHash.equals(this.generateHash()));
        //boolean correctNonce = (this.nonce == WalletService.getNonce(from));
    
        if (hasAddy && hasSignature && correctHash) {
            boolean addyMatch = TransactionVerifier.walletMatch(publicKeyHex, from);
            boolean validOwner = TransactionVerifier.verifySHA256Transaction(publicKeyHex, hexToBytes(txHash), signature);
            boolean senderHasEnough = WalletService.hasCorrectAmount(from, amount, gasFee);
    
            if (addyMatch && validOwner && senderHasEnough) {
                return true; 
            } else {
                System.err.println("❌ lightSyncVerify failed: " + txHash);
                return false;
            }
        } else {
            System.err.println("❌ lightSyncVerify failed basic fields: " + txHash);
            return false;
        }
    }

    public void debugHashValues() {
        System.out.println("From: " + from);
        System.out.println("To: " + to);
        System.out.println("Amount: " + amount);
        System.out.println("Timestamp: " + timeStamp);
        System.out.println("Nonce: " + nonce);
        System.out.println("Data: " + from + to + amount + timeStamp + nonce);
        System.out.println("Generated Hash: " + generateHash());
        System.out.println("Stored Hash: " + txHash);
        System.out.println("Gas Fee In Beantoshi: " + gasFee);
    }
}
