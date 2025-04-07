package com.beanchainbeta.Validation;

import java.util.ArrayList;
import java.util.Iterator;
import com.beanchainbeta.TXs.TX;
import com.beanchainbeta.nodePortal.portal;
import com.beanchainbeta.services.MempoolService;
import com.beanchainbeta.services.WalletService;
import com.beanchainbeta.services.blockchainDB;
import com.beanchainbeta.tools.WalletGenerator;

public class BlockBuilder {
    static ArrayList<TX> acceptedTx = new ArrayList<>();
    static ArrayList<TX> systemTX = new ArrayList<>();

    public static ArrayList<TX> getValidTxList() throws Exception {
        ArrayList<TX> mempool = MempoolService.getTxFromPool();
        ArrayList<TX> validTxs = new ArrayList<>();

        for (TX tx : mempool) {
            if(tx == null){
                //System.out.println("Skipped null transaction in mempool.");
                continue;
            }
            if(tx.getSignature().equals("GENESIS-SIGNATURE")){
                systemTX.add(tx);
                continue;
            }

            if (tx.getSignature() != null && tx.verfifyTransaction()) {
                validTxs.add(tx);
            } else {
                //System.out.println("Invalid TX: " + tx.getTxHash());
                MempoolService.rejectedTransactions.put(tx.getTxHash(), tx);
                //System.out.println(MempoolService.getRejectedTransactions(tx.getFrom()));
            }
        }
        validTxs.sort((a, b) -> Long.compare(b.getGasFee(), a.getGasFee()));

        return validTxs;
    }

    public static void blockMaker(String validatorKey) throws Exception {
        ArrayList<TX> blockTxList = getValidTxList();
        ArrayList<String> txJsonData = new ArrayList<>();
    
        int maxBytes = 1000000;
        int blockBytes = 0;
        long totalGas = 0;
    
        
        Iterator<TX> iterator2 = systemTX.iterator();
        while (iterator2.hasNext() && blockBytes <= maxBytes) {
            TX tx2 = iterator2.next();
            int sizeInBytes = tx2.createJSON().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            blockBytes += sizeInBytes;
            acceptedTx.add(tx2);
            WalletService.genTxProcess(tx2);
            portal.beanchainTest.storeTX(tx2);
            txJsonData.add(tx2.getTxHash());
            iterator2.remove();
        }
    
        
        Iterator<TX> iterator = blockTxList.iterator();
        while (iterator.hasNext() && blockBytes <= maxBytes) {
            TX tx = iterator.next();
            int sizeInBytes = tx.createJSON().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            blockBytes += sizeInBytes;
            acceptedTx.add(tx);

            totalGas += tx.getGasFee();

            WalletService.transfer(tx);
            portal.beanchainTest.storeTX(tx);
            txJsonData.add(tx.getTxHash());
            iterator.remove();
        }

        String addy = WalletGenerator.generateAddress(WalletGenerator.generatePublicKey(WalletGenerator.restorePrivateKey(validatorKey)));
        if (totalGas > 0) {
            TX tx = new TX("BEANX:0xGASPOOL","SYSTEM", addy , totalGas , WalletService.getNonce("BEANX:0xGASPOOL"), 0);
            WalletService.transfer(tx);
            portal.beanchainTest.storeTX(tx);
            txJsonData.add(tx.getTxHash());
            acceptedTx.add(tx);
            System.out.println("Validator " + validatorKey + " rewarded with " + totalGas + " beantoshi");
        }
    
        Block newBlock = new Block(
            blockchainDB.getNextBlockInfo().getBlockHeight(),
            blockchainDB.getNextBlockInfo().getPreviousHash(),
            txJsonData,
            validatorKey
        );
    
        portal.beanchainTest.storeNewBlock(newBlock);
        MempoolService.removeTXs(acceptedTx, MempoolService.rejectedTransactions);
        acceptedTx.clear();  
    }

}