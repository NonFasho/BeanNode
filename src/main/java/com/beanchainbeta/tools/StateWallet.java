package com.beanchainbeta.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

public class StateWallet {
    private String publicKey;
    private String addy;
    private String label;
    private long beantoshi;
    private int nonce;

    public String getPublicKey() {return publicKey;}
    public String getAddy() {return addy;}
    public String getLabel() {return label;}
    public long getBeantoshi() {return beantoshi;}
    public int getNonce() {return nonce;}

    public void setPublicKey(String publicKey) {this.publicKey = publicKey;}
    public void setAddy(String addy) {this.addy = addy;}
    public void setLabel(String label) {this.label = label;}
    public void setBeantoshi(long beantoshi) {this.beantoshi = beantoshi;}
    public void setNonce(int nonce) {this.nonce = nonce;}

    //open for json deserialization
    public StateWallet(){

    }


    //new wallet made from transaction 
    // takes ("to", "label", "Bean")
    public StateWallet(String addy, double bean){
        this.beantoshi = beantoshinomics.toBeantoshi(bean);
        this.addy = addy;
        this.nonce = 0;
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

    public static StateWallet fromJSON(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(json, StateWallet.class);
        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    

}
