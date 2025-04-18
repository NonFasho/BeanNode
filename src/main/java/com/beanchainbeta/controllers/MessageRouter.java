package com.beanchainbeta.controllers;

import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.iq80.leveldb.DBIterator;

import com.bean_core.Block.*;
import com.beanchainbeta.Validation.PendingBlockManager;
import com.beanchainbeta.Validation.TxVerifier;
import com.beanchainbeta.network.Node;
import com.beanchainbeta.network.PeerInfo;
import com.beanchainbeta.nodePortal.portal;
import com.beanchainbeta.services.MempoolService;
import com.beanchainbeta.services.RejectedService;
import com.beanchainbeta.services.WalletService;
import com.beanchainbeta.services.blockchainDB;
import com.bean_core.TXs.*;
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
            case "mempool_summary":
                handleMempoolSummary(message.get("payload"), peer);
                break;
            case "txRequestBatch":
                handleTxRequestBatch(message.get("payload"), peer);
                break;
            case "txBatch":
                handleTxBatch(message.get("payload"));
                break;
            case "tx_rejected": 
                String txHash = message.get("payload").get("txHash").asText();
                System.out.println("🔁 Rejection gossip received for TX: " + txHash);
                MempoolService.removeTxByHash(txHash);
                break;    
            default:
                System.out.println("Unknown message type: " + type);
        }
    }

    private void handleHandshake(JsonNode msg, Socket peer) {
        try {
            // Debug print to verify handshake format
            //System.out.println("Raw handshake payload: " + msg.toPrettyString());
    
            String peerAddress = msg.has("address") ? msg.get("address").asText() : "UNKNOWN";
            int peerHeight = msg.has("blockHeight") ? msg.get("blockHeight").asInt() : 0;
            boolean requestSync = msg.has("requestSync") && msg.get("requestSync").asBoolean();
            String syncMode = msg.has("syncMode") ? msg.get("syncMode").asText() : "FULL";
            boolean isValidator = msg.has("isValidator") && msg.get("isValidator").asBoolean();
            boolean isPublicNode = msg.has("isPublicNode") && msg.get("isPublicNode").asBoolean(); // in case you're also tracking this
            boolean isReply = msg.has("reply") && msg.get("reply").asBoolean();
    
            System.out.println("Received handshake from " + peerAddress +
                " (height=" + peerHeight + ", wantsSync=" + requestSync +
                ", mode=" + syncMode + ", validator=" + isValidator + ", public=" + isPublicNode + ")");
    
            PeerInfo info = new PeerInfo(peer, peerAddress, syncMode, isValidator);
            Node.registerPeer(peer, info);
    
            int myHeight = blockchainDB.getHeight();
    
            if (requestSync && myHeight > peerHeight) {
                sendSyncResponse(peer, peerHeight, syncMode);
            }
            if(!isReply) {
                sendHandshakeBack(peer);
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
                System.out.println("Sent TX_ONLY sync_response to: " + peer.getInetAddress());
    
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
    
            System.out.println("Sent FULL sync_response to " + peer.getInetAddress().getHostAddress() +
                " | Blocks: " + blocksArray.size() +
                " | Confirmed TXs: " + confirmedTxArray.size() +
                " | Mempool TXs: " + mempoolArray.size());
    
        } catch (Exception e) {
            System.err.println("Failed to handle sync_request:");
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
                if (TxVerifier.lightSyncVerify(tx)) {
                    syncTxCache.put(tx.getTxHash(), tx);
                } else {
                    System.err.println("Invalid sync TX: " + tx.getTxHash());
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
                    System.err.println(" Block #" + height + " failed full validation");
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
    
                System.out.println("Saved block #" + height);
                lastValidHash = block.getHash();
                blockCount++;
            }
    
            // 🔹 Stage 3: Import mempool TXs
            for (JsonNode txNode : mempool) {
                TX tx = mapper.treeToValue(txNode, TX.class);
                MempoolService.addTransaction(tx.getTxHash(), tx.createJSON());
            }
            System.out.println("Mempool imported: " + mempool.size());
    
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
                    if (tx != null && TxVerifier.verrifyTransaction(tx)) {
                        orderedTxs.add(tx);
                    } else {
                        System.err.println("Missing or invalid TX in buffered block: " + hash);
                    }
                }
    
                block.setFullTransactions(orderedTxs); // Enable validation
    
                String expectedPrevHash = (height == 0) ? "00000000000000000000" : lastValidHash;
                if (!block.validateBlock(expectedPrevHash)) {
                    System.err.println("Buffered block #" + height + " failed validation");
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
            System.out.println("Sync Complete:");
            System.out.println("   ➤ Confirmed TXs processed: " + txCount);
            System.out.println("   ➤ Blocks saved: " + blockCount);
            System.out.println("   ➤ Buffered blocks: " + bufferedProcessed);
            System.out.println("   ➤ Mempool size: " + mempool.size());
    
        } catch (Exception e) {
            System.err.println("Failed to process sync_response:");
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
            System.out.println("New TX added to mempool: " + txHash);
    
            // 🌐 Gossip to other peers
            Node.broadcastTransactionStatic(tx);

            System.out.println("➡️ Raw incoming TX: " + tx.createJSON());
            //System.out.println("➡️ From: " + tx.getFrom() + " | Nonce: " + tx.getNonce());
            //System.out.println("➡️ Hash: " + tx.getTxHash());
            //System.out.println("➡️ Valid JSON: " + tx.createJSON().contains(tx.getTxHash())); // sanity

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
                System.err.println("Invalid block signature");
                return;
            }

            // Step 2: Height & previous hash
            int localHeight = blockchainDB.getHeight();
            if (block.getHeight() != localHeight + 1) {
                System.err.println("Block height mismatch");
                return;
            }

            Block latestBlock = blockchainDB.getLatestBlock();
            String expectedPreviousHash = latestBlock.getHash();

            String prevHash = expectedPreviousHash;
            if (!block.getPreviousHash().equals(prevHash)) {
                System.err.println("Previous block hash mismatch");
                return;
            }

            ArrayList<TX> txList = new ArrayList<>();
            ConcurrentHashMap<String, TX> rejectedMap = new ConcurrentHashMap<>();

            // Step 1: Retrieve all TXs
            HashMap<String, Integer> simulatedNonces = new HashMap<>();

            for (String hash : block.getTransactions()) {
                TX tx = MempoolService.getTransaction(hash);

                if (tx == null) {
                    System.err.println("Missing TX for incoming block: " + hash);
                    return;
                }

                if (!TxVerifier.verrifyTransaction(tx)) {
                    System.err.println("TX failed verification: " + hash);
                    tx.setStatus("rejected");
                    RejectedService.saveRejectedTransaction(tx);
                    rejectedMap.put(tx.getTxHash(), tx);
                    continue;
                }

                String sender = tx.getFrom();
                int expectedNonce = simulatedNonces.getOrDefault(sender, WalletService.getNonce(sender));

                if (tx.getNonce() != expectedNonce) {
                    System.err.println("❌ Nonce mismatch for TX " + hash + ": expected " + expectedNonce + " but got " + tx.getNonce());
                    tx.setStatus("rejected");
                    RejectedService.saveRejectedTransaction(tx);
                    rejectedMap.put(tx.getTxHash(), tx);
                    continue;
                }

                simulatedNonces.put(sender, expectedNonce + 1);
                txList.add(tx);
            }

            block.setFullTransactions(txList);

            if (!block.validateBlock(expectedPreviousHash)) {
                System.err.println("Incoming block failed validation");
                return;
            }

            for (TX tx : txList) {
                WalletService.transfer(tx);
                tx.setStatus("complete");
                portal.beanchainTest.storeTX(tx);
            }

            portal.beanchainTest.db.put(
                ("block-" + block.getHeight()).getBytes(StandardCharsets.UTF_8),
                block.createJSON().getBytes(StandardCharsets.UTF_8)
            );

            System.out.println("✅ Incoming block #" + block.getHeight() + " accepted and saved.");
            MempoolService.removeTXs(txList, rejectedMap);

        } catch (Exception e) {
            System.err.println("Failed to process incoming block");
            e.printStackTrace();
        }
    }

    private void handleMempoolSummary(JsonNode payload, Socket peer) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode remoteHashesNode = payload.get("txHashes");
            if (remoteHashesNode == null || !remoteHashesNode.isArray()) return;
    
            Set<String> remoteHashes = new HashSet<>();
            for (JsonNode node : remoteHashesNode) {
                remoteHashes.add(node.asText());
            }
    
            Set<String> localHashes = MempoolService.getAllTXHashes();
            Set<String> missingHashes = new HashSet<>(remoteHashes);
            missingHashes.removeAll(localHashes);
    
            if (!missingHashes.isEmpty()) {
                ObjectNode request = mapper.createObjectNode();
                request.put("type", "txRequestBatch");
                ArrayNode reqHashes = mapper.createArrayNode();
                for (String h : missingHashes) reqHashes.add(h);
                request.set("payload", reqHashes);
    
                PrintWriter out = new PrintWriter(peer.getOutputStream(), true);
                out.println(mapper.writeValueAsString(request));
                System.out.println("📥 Requested missing TXs: " + missingHashes.size());
            }

            System.out.println("📥 Received mempool summary from peer: " + peer.getInetAddress());

    
        } catch (Exception e) {
            System.err.println("❌ Failed to handle mempool_summary:");
            e.printStackTrace();
        }
    }

    private void handleTxRequestBatch(JsonNode payload, Socket peer) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<String> requestedHashes = new ArrayList<>();
            for (JsonNode node : payload) requestedHashes.add(node.asText());
    
            ArrayNode txBatch = mapper.createArrayNode();
            for (String hash : requestedHashes) {
                TX tx = MempoolService.getTransaction(hash);
                if (tx != null) {
                    txBatch.add(mapper.readTree(tx.createJSON()));
                }
            }
    
            ObjectNode response = mapper.createObjectNode();
            response.put("type", "txBatch");
            response.set("payload", txBatch);
    
            PrintWriter out = new PrintWriter(peer.getOutputStream(), true);
            out.println(mapper.writeValueAsString(response));
            System.out.println("📤 Sent TX batch with " + txBatch.size() + " TXs");
    
        } catch (Exception e) {
            System.err.println("❌ Failed to handle txRequestBatch:");
            e.printStackTrace();
        }
    }

    private void handleTxBatch(JsonNode payload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            for (JsonNode node : payload) {
                TX tx = mapper.treeToValue(node, TX.class);
                if (!MempoolService.contains(tx.getTxHash())) {
                    MempoolService.addTransaction(tx.getTxHash(), tx.createJSON());
                    System.out.println("🔁 Recovered TX from peer: " + tx.getTxHash());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to handle txBatch:");
            e.printStackTrace();
        }
    }

    private void sendHandshakeBack(Socket peer) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode handshake = mapper.createObjectNode();
            handshake.put("type", "handshake");
            handshake.put("address", portal.admin.address);
            handshake.put("blockHeight", blockchainDB.getHeight());
            handshake.put("requestSync", false); // already syncing the other way
            handshake.put("syncMode", "FULL");
            handshake.put("isValidator", true); // or false if this node isn't a validator
            handshake.put("isPublicNode", true); // optional if you're tracking this
            handshake.put("reply", true);
    
            PrintWriter out = new PrintWriter(peer.getOutputStream(), true);
            out.println(mapper.writeValueAsString(handshake));
    
            System.out.println("↩️ Sent handshake back to peer: " + peer.getInetAddress());
    
        } catch (Exception e) {
            System.err.println("❌ Failed to send handshake back to peer");
            e.printStackTrace();
        }
    }


}
