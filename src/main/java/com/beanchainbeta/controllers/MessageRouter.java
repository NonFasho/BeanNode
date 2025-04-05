package com.beanchainbeta.controllers;

import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.iq80.leveldb.DBIterator;
import com.beanchainbeta.TXs.TX;
import com.beanchainbeta.nodePortal.portal;
import com.beanchainbeta.services.MempoolService;
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
                handleIncomingBlock(message);
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

            System.out.println("Received handshake from " + peerAddress +
                " (height=" + peerHeight + ", wantsSync=" + requestSync + ")");

            int myHeight = blockchainDB.getHeight();

            if (requestSync && myHeight > peerHeight) {
                sendSyncResponse(peer, peerHeight);
            }
        } catch (Exception e) {
            System.out.println("Failed to parse handshake request.");
            e.printStackTrace();
        }
    }

    private void sendSyncResponse(Socket peer, int peerHeight) {
        try {
            PrintWriter out = new PrintWriter(peer.getOutputStream(), true);
            ObjectMapper mapper = new ObjectMapper();

            ArrayNode blocksArray = mapper.createArrayNode();
            ArrayNode txHashArray = mapper.createArrayNode();
            ArrayNode mempoolArray = mapper.createArrayNode();

            int myHeight = blockchainDB.getHeight();

            for (int i = peerHeight + 1; i <= myHeight; i++) {
                byte[] blockBytes = portal.beanchainTest.db.get(("block-" + i).getBytes(StandardCharsets.UTF_8));
                if (blockBytes != null) {
                    JsonNode blockJson = mapper.readTree(new String(blockBytes, StandardCharsets.UTF_8));
                    blocksArray.add(blockJson);
                }
            }

            try (DBIterator iterator = portal.beanchainTest.db.iterator()) {
                for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                    String key = new String(iterator.peekNext().getKey(), StandardCharsets.UTF_8);
                    if (key.startsWith("trans-")) {
                        String txHash = key.substring(6);
                        txHashArray.add(txHash);
                    }
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
            response.set("txHashes", txHashArray);
            response.set("mempool", mempoolArray);

            out.println(mapper.writeValueAsString(response));

            System.out.println("Sent sync_response with " +
                blocksArray.size() + " blocks, " +
                txHashArray.size() + " tx hashes, " +
                mempoolArray.size() + " mempool txs.");

        } catch (Exception e) {
            System.out.println("Failed to send sync_response.");
            e.printStackTrace();
        }
    }

    private void handleSyncRequest(JsonNode message, Socket peer) {
        System.out.println("Received sync_request — not yet implemented.");
        // Future implementation: reply with sync_response
    }

    private void handleSyncResponse(JsonNode msg) {
        System.out.println("Received sync_response — processing not yet implemented.");
        // Future: parse blocks, update chain, validate mempool
    }

    private void handleIncomingTransaction(JsonNode msg) {
        System.out.println("Received transaction — processing not yet implemented.");
        // Future: Validate and add to mempool
    }

    private void handleIncomingBlock(JsonNode msg) {
        System.out.println("Received block — processing not yet implemented.");
        // Future: Validate and add to chain
    }
}
