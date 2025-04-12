package com.beanchainbeta.Validation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.beanchainbeta.TXs.TX;
import com.beanchainbeta.services.blockchainDB;
import com.beanchainbeta.services.blockchainDB.BlockInfo;
import com.beanchainbeta.tools.SHA256TransactionSigner;
import com.beanchainbeta.tools.WalletGenerator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Block {
    private int height;
    private String previousHash;
    private String hash;
    private String merkleroot;
    private List<String> transactions;
    private long timeStamp;
    private String validatorPubKey;
    private String signature;
    
    @JsonIgnore
    private transient List<TX> fullTransactions = new ArrayList<>();

    public int getHeight() {return height;}
    public String getMerkleRoot() {return merkleroot;}
    public long getTimeStamp() {return timeStamp;}
    public String getPreviousHash() {return previousHash;}
    public String getHash() {return hash;}
    public List<String> getTransactions() {return transactions;}
    public String getValidatorPubKey() {return validatorPubKey;}
    public String getSignature() {return signature;}

    public void setHeight(int height) {this.height = height;}
    public void setMerkleRoot(String merkleroot) {this.merkleroot = merkleroot;}
    public void setTimeStamp(long timeStamp) {this.timeStamp = timeStamp;}
    public void setPreviousHash(String previousHash) {this.previousHash = previousHash;}
    public void setHash(String hash) {this.hash = hash;}
    public void setTransactions(List<String> transactions) {this.transactions = transactions;}
    public void setValidatorPubKey(String validatorPubKey) {this.validatorPubKey = validatorPubKey;}
    public void setSignature(String signature) {this.signature = signature;}

    public Block() {

    }
    

    public Block(int height, String previousHash, List<String> transactions, String validatorPrivKey) throws Exception {
        this.height = height;
        this.previousHash = previousHash;
        this.timeStamp = System.currentTimeMillis();
        this.transactions = transactions;
        this.merkleroot = calculateMerkleRoot();
        this.hash = calculateBlockHash();
        sign(WalletGenerator.restorePrivateKey(validatorPrivKey));
    }

    public String calculateMerkleRoot() {
        return calculateMerkleRoot(this.transactions);
    }

    public String calculateBlockHash(){
        try {
            String data = height + previousHash + merkleroot;
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

    private String hash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for(byte b: hash) {
                hexString.append(String.format("%02x", b));
            } 
            return hexString.toString();
        } catch (Exception e){
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

    public static Block fromJSON(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, Block.class);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    public void sign(PrivateKey privateKey) throws Exception{
        validatorPubKey = WalletGenerator.generatePublicKey(privateKey);
        
        byte[] transactionHash = hexToBytes(this.hash);
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

    public boolean signatureValid() throws Exception{
        if(TransactionVerifier.verifySHA256Transaction(this.validatorPubKey, hexToBytes(hash), this.signature)) {
            return true;
        } else {
            return false;
        }
    }

    public boolean validateBlock(String expectedPrevHash) throws Exception {
    
        boolean merkleValid = this.getMerkleRoot().equals(this.calculateMerkleRoot());
        boolean hashValid = this.getHash().equals(this.calculateBlockHash());
        boolean signatureValid = this.signatureValid();
        boolean previousHashValid = this.getPreviousHash().equals(expectedPrevHash);
    
        if (merkleValid && hashValid && signatureValid && previousHashValid) {
            return true;
        } else {
            System.err.println("❌ Block failed validation:");
            if (!merkleValid) System.err.println(" - Merkle root mismatch");
            if (!hashValid) System.err.println(" - Hash mismatch");
            if (!signatureValid) System.err.println(" - Invalid signature");
            if (!previousHashValid) {
                System.err.println(" - Invalid Previous Hash");
                System.err.println("   ➤ Expected: " + expectedPrevHash);
                System.err.println("   ➤ Found:    " + this.getPreviousHash());
            }
            return false;
        }
    }

    public static String calculateMerkleRoot(List<String> txHashes) {
        if (txHashes == null || txHashes.isEmpty()) return "";

        List<String> currentLevel = new ArrayList<>(txHashes);

        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>();

            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left = currentLevel.get(i);
                String right = (i + 1 < currentLevel.size()) ? currentLevel.get(i + 1) : left;
                nextLevel.add(sha256(left + right));
            }

            currentLevel = nextLevel;
        }

        return currentLevel.get(0);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }

    public boolean verifyMerkleRoot() {
        String recalculatedRoot = calculateMerkleRoot();
        return this.merkleroot.equals(recalculatedRoot);
    }

    public void setFullTransactions(List<TX> fullTransactions) {
        this.fullTransactions = fullTransactions;
    }
    
    public List<TX> getFullTransactions() {
        return fullTransactions;
    }
    
}
