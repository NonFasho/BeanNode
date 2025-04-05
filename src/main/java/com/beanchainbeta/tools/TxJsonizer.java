package com.beanchainbeta.tools;

import com.beanchainbeta.TXs.TX;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TxJsonizer {
    public static String txHash;
    public static String transactionJson;

    public String getTxHash() {return txHash;}
    public String getTransactionJson() {return transactionJson;}

    public TxJsonizer(){

    }

    public TxJsonizer(TX tx){
        txHash = tx.getTxHash();
        transactionJson = tx.createJSON();

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
  
}
    
    
        


