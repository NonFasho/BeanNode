package com.beanchainbeta.services;

import org.iq80.leveldb.*;
import com.beanchainbeta.TXs.GenesisTX;
import com.beanchainbeta.TXs.TX;
import com.beanchainbeta.Validation.Block;
import com.beanchainbeta.controllers.DBManager;
import com.beanchainbeta.tools.WalletGenerator;
import java.nio.charset.StandardCharsets;
import static org.iq80.leveldb.impl.Iq80DBFactory.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


public class blockchainDB {
    public static DB db;
    public static int currentHeight;
    public static String previousHash;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    public blockchainDB() {
        this.db = DBManager.getDB("BeanChainDBTest"); 

        try {
            if (!checkGenBlock()) {
                loadGenBlock();
            }
        } catch (Exception e) {
            System.err.println("Error initializing blockchainDB: " + e.getMessage());
        }
    }

    private void storeBlock(Block block) {
        try {
            db.put(bytes("block-0"), bytes(block.createJSON()));
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public void storeNewBlock(Block block) throws Exception {
        //System.out.println("CHECK****");
        if(block.signatureValid()) {
            try{
                String key = "block-" + block.getHeight();
                db.put(bytes(key), bytes(block.createJSON()));
                String blockLog = 
                "{NEW-BLOCK}{" + block.getHeight() + "}\n" +
                block.createJSON(); 
                System.out.println(blockLog);
            } catch (Exception e) {
                System.err.println(e);
            }
        } else {
            System.out.println("ERROR INVALID BLOCK");
        }
    }


    private Boolean checkGenBlock() {
        byte[] data = db.get(bytes("block-0"));
        return data != null;

    }
    public static Boolean checkBlock() {
        byte[] data = db.get(bytes("block-1"));
        return data != null;

    }

    public String getData(String key) {
        return asString(db.get(bytes(key)));
    }

    

    public void storeTX(TX TX) {
        try {
            db.put(bytes("tran-" + TX.getTxHash()), bytes(TX.createJSON()));
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public void closeDB() {
        try {
            db.close();
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    private void loadGenBlock() throws Exception{
        GenesisTX genTX1 = new GenesisTX("BEANX:0xFAUCET", 5000000); //need funds released programatically 
        GenesisTX genTX2 = new GenesisTX("BEANX:0xEARLYWALLET", 5000000); //need funds released programatically 
        GenesisTX genTX3 = new GenesisTX("BEANX:0xSTAKEREWARDS", 15000000); //need funds released programatically
        GenesisTX genTX4 = new GenesisTX("BEANX:0xNODEREWARDS", 30000000); //need funds released programatically 
        GenesisTX genTX5 = new GenesisTX("BEANX:0x1c8496175b3f4802e395db5fab4dd66e09c431b2", 2500000); // needs to be sent to wallet in chunks over time to avoid rug pull
        GenesisTX genTX6 = new GenesisTX("BEANX:0xLIQUIDITY", 12500000); // held by the team promised to be used for liquidity or aborted and transfered to rewards based on future vote

        WalletService.genBlock();
        
        List<String> genesisTransactions = Arrays.asList(
            genTX1.getTxHash(), genTX2.getTxHash(), genTX3.getTxHash(), genTX4.getTxHash(), genTX5.getTxHash(), genTX6.getTxHash()
        );

        String genPrivateKey = WalletGenerator.generatePrivateKey();

        Block genesisBlock = new Block(0, "00000000000000000000", genesisTransactions, genPrivateKey);

        


        storeBlock(genesisBlock);
        genPrivateKey = "";
        List<TX> confirmed = Arrays.asList(genTX1, genTX2, genTX3, genTX4, genTX5, genTX6);

        for(TX t: confirmed){
            storeTX(t);
        }
    }

    public List<Block> getAllBlocks() {
    List<Block> blocks = new ArrayList<>();

    try (DBIterator iterator = db.iterator()) {
        iterator.seekToFirst();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String key = asString(entry.getKey());

            if (key.startsWith("block-")) {
                String json = asString(entry.getValue());
                System.out.print(key);
                blocks.add(Block.fromJSON(json)); // You’ll need this method
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    return blocks;
    }

    public List<TX> getAllTransactions() {
        List<TX> txs = new ArrayList<>();

        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                String key = asString(entry.getKey());

                if (key.startsWith("tran-")) {
                    String json = asString(entry.getValue());
                    //System.out.println(json);
                    txs.add(TX.fromJSON(json)); 
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return txs;
    }

    public static List<TX> getWalletCompleteTXs(String addy) {
        List<TX> txs = new ArrayList<>();

        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                String key = asString(entry.getKey());

                if (key.startsWith("tran-")) {
                    String json = asString(entry.getValue());
                    //System.out.println(json);
                    TX tx = TX.fromJSON(json); 
                    if(tx.getFrom().equals(addy)) {
                        txs.add(tx);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        txs.sort((a, b) -> Long.compare(b.getTimeStamp(), a.getTimeStamp()));
        return txs;
    }

    public static List<TX> getWalletInTXs(String addy) {
        List<TX> txs = new ArrayList<>();

        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                String key = asString(entry.getKey());

                if (key.startsWith("tran-")) {
                    String json = asString(entry.getValue());
                    //System.out.println(json);
                    TX tx = TX.fromJSON(json); 
                    if(tx.getTo().equals(addy)) {
                        txs.add(tx);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        txs.sort((a, b) -> Long.compare(b.getTimeStamp(), a.getTimeStamp()));
        return txs;
    }


    public static BlockInfo getNextBlockInfo() {
        Block latestBlock = getLatestBlock();

        if (latestBlock == null) {
            // Default to genesis block values if no blocks exist
            return new BlockInfo("00000000000000000000", 0);
        }

        return new BlockInfo(latestBlock.getHash(), latestBlock.getHeight() + 1);
    }

    // Method to fetch the latest block from LevelDB
    private static Block getLatestBlock() {
        Block latestBlock = null;
        int highestHeight = -1;

        try (DBIterator iterator = db.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                String key = asString(entry.getKey());

                if (key.startsWith("block-")) {
                    String json = asString(entry.getValue());
                    Block block = Block.fromJSON(json); 

                    if (block.getHeight() > highestHeight) {
                        highestHeight = block.getHeight();
                        latestBlock = block;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return latestBlock;
    }

    // Helper class to return both previousHash and blockHeight
    public static class BlockInfo {
        private static String previousHash;
        private static int blockHeight;

        public BlockInfo(String previousHash, int blockHeight) {
            this.previousHash = previousHash;
            this.blockHeight = blockHeight;
        }

        public String getPreviousHash() {
            return previousHash;
        }

        public int getBlockHeight() {
            return blockHeight;
        }
    }

    public static int getHeight() {
    int maxHeight = -1;

    try (DBIterator iterator = db.iterator()) {
        for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
            String key = new String(iterator.peekNext().getKey(), StandardCharsets.UTF_8);

            if (key.startsWith("block-")) {
                try {
                    int height = Integer.parseInt(key.substring(6)); // "block-" is 6 chars
                    if (height > maxHeight) {
                        maxHeight = height;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid block key: " + key);
                }
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    return maxHeight;
    }
}

