package com.beanchainbeta.Validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.beanchainbeta.TXs.TX;
import com.beanchainbeta.network.Node;
import com.beanchainbeta.nodePortal.portal;
import com.beanchainbeta.services.MempoolService;
import com.beanchainbeta.services.WalletService;
import com.beanchainbeta.services.blockchainDB;
import com.beanchainbeta.tools.WalletGenerator;
import com.beanchainbeta.tools.beantoshinomics;

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
                // Store in rejectedDB
                MempoolService.rejectedTransactions.put(tx.getTxHash(), tx);
                //System.out.println(MempoolService.getRejectedTransactions(tx.getFrom()));
            }
        }

        Map<String, List<TX>> groupedBySender = new HashMap<>();
        for (TX tx : validTxs) {
            groupedBySender
                .computeIfAbsent(tx.getFrom(), k -> new ArrayList<>())
                .add(tx);
                //System.out.println("TX: " + tx.getTxHash() + " GAS: " + tx.getGasFee());
        }

        // Sort each sender's TXs by nonce
        for (List<TX> txList : groupedBySender.values()) {
            txList.sort(Comparator.comparingInt(TX::getNonce));
        }

        // Flatten back into list, sorting by the gas fee of the first tx in each sender group
        validTxs = groupedBySender.values().stream()
            .sorted((a, b) -> Long.compare(b.get(0).getGasFee(), a.get(0).getGasFee()))
            .flatMap(List::stream)
            .collect(Collectors.toCollection(ArrayList::new));

        return validTxs;
    }

    public static void blockMaker(String validatorKey) throws Exception {
        ArrayList<TX> blockTxList = getValidTxList();
        ArrayList<String> txJsonData = new ArrayList<>();
    
        int maxBytes = 1000000;
        int blockBytes = 0;
        long totalGas = 0;
    
        systemTX.sort(Comparator.comparing(TX::getTxHash));
        Iterator<TX> iterator2 = systemTX.iterator();
        while (iterator2.hasNext() && blockBytes <= maxBytes) {
            TX tx2 = iterator2.next();
            int sizeInBytes = tx2.createJSON().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            blockBytes += sizeInBytes;
            acceptedTx.add(tx2);
            tx2.setStatus("complete");
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
            tx.setStatus("complete");

            //LOG
            System.out.println("TX GAS FE: "+ tx.getGasFee());

            totalGas += tx.getGasFee();
            
            //LOG
            System.out.println("CURRENT GAS:" + totalGas);

            WalletService.transfer(tx);
            portal.beanchainTest.storeTX(tx);
            txJsonData.add(tx.getTxHash());
            iterator.remove();
        }
        //LOG
        System.out.println(totalGas);

        String addy = WalletGenerator.generateAddress(WalletGenerator.generatePublicKey(WalletGenerator.restorePrivateKey(validatorKey)));
        if (totalGas > 0) {
            TX tx = new TX("BEANX:0xGASPOOL","SYSTEM", addy , beantoshinomics.toBean(totalGas) , WalletService.getNonce("BEANX:0xGASPOOL"), 0);
            tx.setSignature("GENESIS-SIGNATURE");
            WalletService.transfer(tx);
            tx.setStatus("complete");
            //LOG
            //System.out.print(WalletService.getBeanBalance(addy));
            portal.beanchainTest.storeTX(tx);
            txJsonData.add(tx.getTxHash());
            Node.broadcastTransactionStatic(tx);
            acceptedTx.add(tx);
            //System.out.println("Validator " + validatorKey + " rewarded with " + totalGas + " beantoshi");
        }
    
        Block newBlock = new Block(
            blockchainDB.getNextBlockInfo().getBlockHeight(),
            blockchainDB.getNextBlockInfo().getPreviousHash(),
            txJsonData,
            validatorKey
        );
    
        portal.beanchainTest.storeNewBlock(newBlock);
        Node.broadcastBlock(newBlock);
        MempoolService.removeTXs(acceptedTx, MempoolService.rejectedTransactions);
        acceptedTx.clear(); 
        MempoolService.rejectedTransactions.clear(); 
    }

}