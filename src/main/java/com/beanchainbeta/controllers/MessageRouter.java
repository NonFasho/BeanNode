package com.beanchainbeta.controllers;

import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.iq80.leveldb.DBIterator;
import com.beanchainbeta.TXs.TX;
import com.beanchainbeta.Validation.Block;
import com.beanchainbeta.Validation.PendingBlockManager;
import com.beanchainbeta.network.Node;
import com.beanchainbeta.network.PeerInfo;
import com.beanchainbeta.nodePortal.portal;
import com.beanchainbeta.services.MempoolService;
import com.beanchainbeta.services.WalletService;
import com.beanchainbeta.services.blockchainDB;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MessageRouter {

    public MessageRouter() {}

    public void route(JsonNode message, Socket peer) {
        if (!message.has("type")) {
            System.out.println("Invalid message (missing 'type')");
            return;
        }

        String type = message.get("type").asText();

        switch (type) {
            case "handshake":
                handleHandshake(message, peer);
                break;
            case "sync_request":
                handleSyncRequest(message, peer);
                break;
            case "sync_response":
                handleSyncResponse(message);
                break;
            case "transaction":
                handleIncomingTransaction(message);
                break;
            case "block":
                handleIncomingBlock(message, peer);
                break;
            default:
                System.out.println("Unknown message type: " + type);
        }
    }

    private void handleHandshake(JsonNode msg, Socket peer) {
    try {
        String peerAddress = msg.get("address").asText();
        int peerHeight = msg.get("blockHeight").asInt();
        boolean requestSync = msg.get("requestSync").asBoolean(); 
        String syncMode = msg.has("syncMode") ? msg.get("syncMode").asText() : "FULL";
        boolean isValidator = msg.get("isValidator").asBoolean();

        System.out.println("Received handshake from " + peerAddress +
            " (height=" + peerHeight + ", wantsSync=" + requestSync + 
            ", mode=" + syncMode + ")");

        // ✅ Save this peer's sync mode for future decisions
        PeerInfo info = new PeerInfo(peer, peerAddress, syncMode, isValidator);
        Node.registerPeer(peer, info); // <— you need this helper method in Node class

        int myHeight = blockchainDB.getHeight();

        if (requestSync && myHeight > peerHeight) {
            sendSyncResponse(peer, peerHeight, syncMode);
        }
    } catch (Exception e) {
        System.out.println("Failed to parse handshake request.");
        e.printStackTrace();
    }
}

    private void sendSyncResponse(Socket peer, int peerHeight, String syncMode) {
        try {
            PrintWriter out = new PrintWriter(peer.getOutputStream(), true);
            ObjectMapper mapper = new ObjectMapper();
    
            ArrayNode blocksArray = mapper.createArrayNode();
            ArrayNode txArray = mapper.createArrayNode();
            ArrayNode mempoolArray = mapper.createArrayNode();
    
            int myHeight = blockchainDB.getHeight();
    
            // 🧱 Add blocks only if full sync
            if (!"TX_ONLY".equalsIgnoreCase(syncMode)) {
                for (int i = peerHeight + 1; i <= myHeight; i++) {
                    byte[] blockBytes = portal.beanchainTest.db.get(("block-" + i).getBytes(StandardCharsets.UTF_8));
                    if (blockBytes != null) {
                        JsonNode blockJson = mapper.readTree(new String(blockBytes, StandardCharsets.UTF_8));
                        blocksArray.add(blockJson);
                    }
                }
            }
    
            // ✅ Always add confirmed TXs
            try (DBIterator iterator = portal.beanchainTest.db.iterator()) {
                for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                    String key = new String(iterator.peekNext().getKey(), StandardCharsets.UTF_8);
                    if (key.startsWith("tran-")) {
                        String txJson = new String(iterator.peekNext().getValue(), StandardCharsets.UTF_8);
                        txArray.add(mapper.readTree(txJson));
                    }
                }
            }
    
            // 🧃 Add mempool only if full sync
            if (!"TX_ONLY".equalsIgnoreCase(syncMode)) {
                for (TX tx : MempoolService.getTxFromPool()) {
                    String txJson = mapper.writeValueAsString(tx);
                    mempoolArray.add(mapper.readTree(txJson));
                }
            }
    
            // 📨 Build response
            ObjectNode response = mapper.createObjectNode();
            response.put("type", "sync_response");
            response.put("latestHeight", myHeight);
            response.set("confirmedTxs", txArray);
    
            if (!"TX_ONLY".equalsIgnoreCase(syncMode)) {
                response.set("blocks", blocksArray);
                response.set("mempool", mempoolArray);
            }
    
            out.println(mapper.writeValueAsString(response));
    
            System.out.println("📤 Sent " + (syncMode.equalsIgnoreCase("TX_ONLY") ? "TX_ONLY" : "FULL") +
                " sync_response to " + peer.getInetAddress().getHostAddress() +
                " | Confirmed TXs: " + txArray.size() +
                (syncMode.equalsIgnoreCase("TX_ONLY") ? "" :
                    " | Blocks: " + blocksArray.size() +
                    " | Mempool TXs: " + mempoolArray.size()));
    
        } catch (Exception e) {
            System.out.println("❌ Failed to send sync_response.");
            e.printStackTrace();
        }
    }

    private void handleSyncRequest(JsonNode message, Socket peer) {
        try {
            PrintWriter out = new PrintWriter(peer.getOutputStream(), true);
            ObjectMapper mapper = new ObjectMapper();
    
            String syncMode = message.has("syncMode") ? message.get("syncMode").asText() : "FULL";
    
            ArrayNode blocksArray = mapper.createArrayNode();
            ArrayNode confirmedTxArray = mapper.createArrayNode();
            ArrayNode mempoolArray = mapper.createArrayNode();
    
            int myHeight = blockchainDB.getHeight();
    
            // 🔹 Step 1: Always load confirmed TXs
            Set<String> blockTxHashes = new HashSet<>();
            for (int i = 0; i <= myHeight; i++) {
                byte[] blockBytes = portal.beanchainTest.db.get(("block-" + i).getBytes(StandardCharsets.UTF_8));
                if (blockBytes != null) {
                    JsonNode blockJson = mapper.readTree(new String(blockBytes, StandardCharsets.UTF_8));
                    blocksArray.add(blockJson);

                    JsonNode txList = blockJson.get("transactions");
                    if (txList != null && txList.isArray()) {
                        for (JsonNode txHashNode : txList) {
                            blockTxHashes.add(txHashNode.asText());
                        }
                    }
                }
            }

            for (String txHash : blockTxHashes) {
                byte[] txBytes = portal.beanchainTest.db.get(("tran-" + txHash).getBytes(StandardCharsets.UTF_8));
                if (txBytes != null) {
                    confirmedTxArray.add(mapper.readTree(new String(txBytes, StandardCharsets.UTF_8)));
                } else {
                    System.err.println("Could not find TX from block: " + txHash);
                }
            }
    
            if (syncMode.equalsIgnoreCase("TX_ONLY")) {
                // ✂️ Skip blocks and mempool
                ObjectNode response = mapper.createObjectNode();
                response.put("type", "sync_response");
                response.put("latestHeight", myHeight);
                response.set("confirmedTxs", confirmedTxArray);
    
                out.println(mapper.writeValueAsString(response));
                System.out.println("📤 Sent TX_ONLY sync_response to: " + peer.getInetAddress());
    
                return;
            }
    
            // 🔹 Otherwise, do full sync
            for (int i = 0; i <= myHeight; i++) {
                byte[] blockBytes = portal.beanchainTest.db.get(("block-" + i).getBytes(StandardCharsets.UTF_8));
                if (blockBytes != null) {
                    JsonNode blockJson = mapper.readTree(new String(blockBytes, StandardCharsets.UTF_8));
                    blocksArray.add(blockJson);
                }
            }
    
            for (TX tx : MempoolService.getTxFromPool()) {
                String txJson = mapper.writeValueAsString(tx);
                mempoolArray.add(mapper.readTree(txJson));
            }
    
            ObjectNode response = mapper.createObjectNode();
            response.put("type", "sync_response");
            response.put("latestHeight", myHeight);
            response.set("blocks", blocksArray);
            response.set("confirmedTxs", confirmedTxArray);
            response.set("mempool", mempoolArray);
    
            out.println(mapper.writeValueAsString(response));
    
            System.out.println("📡 Sent FULL sync_response to " + peer.getInetAddress().getHostAddress() +
                " | Blocks: " + blocksArray.size() +
                " | Confirmed TXs: " + confirmedTxArray.size() +
                " | Mempool TXs: " + mempoolArray.size());
    
        } catch (Exception e) {
            System.err.println("❌ Failed to handle sync_request:");
            e.printStackTrace();
        }
    }
    

    private void handleSyncResponse(JsonNode msg) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode blocks = msg.get("blocks");
            JsonNode confirmedTxs = msg.get("confirmedTxs");
            JsonNode mempool = msg.get("mempool");
    
            int blockCount = 0;
            int txCount = 0;
    
            // 🔹 Stage 1: Cache all confirmed TXs
            Map<String, TX> syncTxCache = new HashMap<>();
            for (JsonNode txNode : confirmedTxs) {
                TX tx = mapper.treeToValue(txNode, TX.class);
                if (tx.lightSyncVerify()) {
                    syncTxCache.put(tx.getTxHash(), tx);
                } else {
                    System.err.println("❌ Invalid sync TX: " + tx.getTxHash());
                }
            }
    
            // 🔹 Stage 2: Process blocks in order
            Block latest = blockchainDB.getLatestBlock();
            String lastValidHash = (latest != null) ? latest.getHash() : "00000000000000000000";
    
            for (JsonNode blockNode : blocks) {
                Block block = mapper.treeToValue(blockNode, Block.class);
                int height = block.getHeight();
                String key = "block-" + height;
    
                if (portal.beanchainTest.db.get(key.getBytes(StandardCharsets.UTF_8)) != null) {
                    continue; // already stored
                }
    
                // Pull TXs from sync cache
                List<String> txHashes = block.getTransactions();
                List<TX> orderedTxs = new ArrayList<>();
                for (String hash : txHashes) {
                    TX tx = syncTxCache.get(hash);
                    if (tx == null) {
                        System.err.println("Missing TX for block: " + hash);
                        continue;
                    }
                    orderedTxs.add(tx);
                }
    
                block.setFullTransactions(orderedTxs); // Set full TXs to enable merkle/hash validation
    
                if (!block.validateBlock(lastValidHash)) {
                    System.err.println("❌ Block #" + height + " failed full validation");
                    continue;
                }
    
                // Apply TXs to wallet state
                for (TX tx : orderedTxs) {
                    WalletService.transfer(tx);
                    portal.beanchainTest.storeTX(tx);
                    txCount++;
                }
    
                portal.beanchainTest.db.put(
                    key.getBytes(StandardCharsets.UTF_8),
                    block.createJSON().getBytes(StandardCharsets.UTF_8)
                );
    
                System.out.println("✅ Saved block #" + height);
                lastValidHash = block.getHash();
                blockCount++;
            }
    
            // 🔹 Stage 3: Import mempool TXs
            for (JsonNode txNode : mempool) {
                TX tx = mapper.treeToValue(txNode, TX.class);
                MempoolService.addTransaction(tx.getTxHash(), tx.createJSON());
            }
            System.out.println("📥 Mempool imported: " + mempool.size());
    
            // 🔹 Stage 4: Process buffered blocks
            List<Block> bufferedBlocks = PendingBlockManager.getBufferedBlocks();
            int bufferedProcessed = 0;
            lastValidHash = blockchainDB.getLatestBlock().getHash();
    
            for (Block block : bufferedBlocks) {
                int height = block.getHeight();
                String key = "block-" + height;
    
                if (portal.beanchainTest.db.get(key.getBytes(StandardCharsets.UTF_8)) != null) {
                    lastValidHash = block.getHash(); // advance if somehow already saved
                    continue;
                }
    
                // Try getting TXs from sync cache or mempool
                List<TX> orderedTxs = new ArrayList<>();
                for (String hash : block.getTransactions()) {
                    TX tx = syncTxCache.get(hash);
                    if (tx == null) tx = MempoolService.getTransaction(hash); // fallback
                    if (tx != null && tx.verfifyTransaction()) {
                        orderedTxs.add(tx);
                    } else {
                        System.err.println("❌ Missing or invalid TX in buffered block: " + hash);
                    }
                }
    
                block.setFullTransactions(orderedTxs); // Enable validation
    
                String expectedPrevHash = (height == 0) ? "00000000000000000000" : lastValidHash;
                if (!block.validateBlock(expectedPrevHash)) {
                    System.err.println("❌ Buffered block #" + height + " failed validation");
                    continue;
                }
    
                for (TX tx : orderedTxs) {
                    WalletService.transfer(tx);
                    portal.beanchainTest.storeTX(tx);
                }
    
                portal.beanchainTest.db.put(
                    key.getBytes(StandardCharsets.UTF_8),
                    block.createJSON().getBytes(StandardCharsets.UTF_8)
                );
    
                lastValidHash = block.getHash();
                bufferedProcessed++;
            }
    
            PendingBlockManager.clearBufferedBlocks();
    
            // 🔹 Final Summary
            portal.setIsSyncing(false);
            System.out.println("✅ Sync Complete:");
            System.out.println("   ➤ Confirmed TXs processed: " + txCount);
            System.out.println("   ➤ Blocks saved: " + blockCount);
            System.out.println("   ➤ Buffered blocks: " + bufferedProcessed);
            System.out.println("   ➤ Mempool size: " + mempool.size());
    
        } catch (Exception e) {
            System.err.println("❌ Failed to process sync_response:");
            e.printStackTrace();
        }
    }

    private void handleIncomingTransaction(JsonNode msg) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            TX tx = mapper.treeToValue(msg.get("payload"), TX.class);
            String txHash = tx.getTxHash();
    
            // 🔁 Check if already in mempool
            if (MempoolService.contains(txHash)) {
                System.out.println("🔄 Duplicate TX received, already in mempool: " + txHash);
                return;
            }
    
            // ✅ Add and broadcast
            MempoolService.addTransaction(txHash, tx.createJSON());
            System.out.println("➕ New TX added to mempool: " + txHash);
    
            // 🌐 Gossip to other peers
            Node.broadcastTransactionStatic(tx);

            System.out.println("➡️ Raw incoming TX: " + tx.createJSON());
            System.out.println("➡️ From: " + tx.getFrom() + " | Nonce: " + tx.getNonce());
            System.out.println("➡️ Hash: " + tx.getTxHash());
            System.out.println("➡️ Valid JSON: " + tx.createJSON().contains(tx.getTxHash())); // sanity

            TX pulled = MempoolService.getTransaction(tx.getTxHash());
            if (pulled == null) {
                System.err.println("🛑 TX was not saved in memory map.");
            } else {
                System.out.println("✅ TX saved in mempool memory.");
            }

    
        } catch (Exception e) {
            System.err.println("❌ Error handling incoming TX");
            e.printStackTrace();
        }
    }
    

    private void handleIncomingBlock(JsonNode msg, Socket peer) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = msg.get("payload");
            Block block = mapper.treeToValue(payload, Block.class);

            if (portal.isSyncing) {
                PendingBlockManager.bufferDuringSync(block);
                System.out.println("Held block #" + block.getHeight() + " during sync.");
                return;
            }

            // Step 1: Signature check
            if (!block.signatureValid()){
                System.err.println("❌ Invalid block signature");
                return;
            }

            // Step 2: Height & previous hash
            int localHeight = blockchainDB.getHeight();
            if (block.getHeight() != localHeight + 1) {
                System.err.println("❌ Block height mismatch");
                return;
            }

            Block latestBlock = blockchainDB.getLatestBlock();
            String expectedPreviousHash = latestBlock.getHash();

            String prevHash = expectedPreviousHash;
            if (!block.getPreviousHash().equals(prevHash)) {
                System.err.println("❌ Previous block hash mismatch");
                return;
            }

            List<TX> txList = new ArrayList<>();
            for (String hash : block.getTransactions()) {
                TX tx = MempoolService.getTransaction(hash);
                if (tx == null) {
                    System.err.println("❌ Missing TX for incoming block: " + hash);
                    return;
                }
                if (!tx.verfifyTransaction()) {
                    System.err.println("❌ TX failed full verification in incoming block: " + hash);
                    return;
                }
                txList.add(tx);
            }

            block.setFullTransactions(txList);

            if (!block.validateBlock(expectedPreviousHash)) {
                System.err.println("❌ Incoming block failed validation");
                return;
            }

            for (TX tx : txList) {
                WalletService.transfer(tx);
                portal.beanchainTest.storeTX(tx);
            }

            portal.beanchainTest.db.put(
                ("block-" + block.getHeight()).getBytes(StandardCharsets.UTF_8),
                block.createJSON().getBytes(StandardCharsets.UTF_8)
            );

            System.out.println("✅ Incoming block #" + block.getHeight() + " accepted and saved.");

        } catch (Exception e) {
            System.err.println("❌ Failed to process incoming block");
            e.printStackTrace();
        }
    }


}
