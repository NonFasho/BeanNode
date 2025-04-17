// Updated Node.java to be resilient to disconnected peers
package com.beanchainbeta.network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.beanchainbeta.TXs.TX;
import com.beanchainbeta.Validation.Block;
import com.beanchainbeta.controllers.MessageRouter;
import com.beanchainbeta.nodePortal.portal;
import com.beanchainbeta.services.blockchainDB;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

public class Node {
    private final int port = 6442;
    private String ip;
    private final ServerSocket serverSocket;
    private final Set<Socket> connectedPeers = ConcurrentHashMap.newKeySet();
    private final List<String> knownAddresses = new CopyOnWriteArrayList<>();
    private static Node instance;
    private static String syncMode = "FULL";
    private static ConcurrentHashMap<Socket, PeerInfo> peers = new ConcurrentHashMap<>();

    public static void initialize(String ip) throws IOException {
        if (instance == null) {
            instance = new Node(ip);
        }
    }

    public static Node getInstance() {
        return instance;
    }

    public Node(String ip) throws IOException {
        this.ip = ip;
        InetAddress bindAddress = ip.equals("0.0.0.0") ? InetAddress.getByName("0.0.0.0") : InetAddress.getByName(ip);
        this.serverSocket = new ServerSocket(port, 100, bindAddress);
    }

    public void start() {
        System.out.println("NodeBeta listening on: " + ip + ":" + port);
        new Thread(this::listenForPeers).start();
    }

    public void listenForPeers() {
        while (true) {
            try {
                Socket peer = serverSocket.accept();
                connectedPeers.add(peer);
                System.out.println("New peer connected: " + peer.getInetAddress().getHostAddress());
                new Thread(() -> handleIncomingMessages(peer)).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleIncomingMessages(Socket peer) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(peer.getInputStream()))) {
            ObjectMapper mapper = new ObjectMapper();
            MessageRouter router = new MessageRouter();
            String line;
            while ((line = in.readLine()) != null) {
                try {
                    JsonNode message = mapper.readTree(line);
                    router.route(message, peer);
                } catch (Exception e) {
                    System.err.println("Failed to parse incoming JSON: " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("Connection lost with peer: " + peer.getInetAddress());
        } finally {
            try {
                peer.close();
            } catch (IOException ignored) {}
            connectedPeers.remove(peer);
        }
    }

    public void broadcast(String message) {
        for (Socket peer : new ArrayList<>(connectedPeers)) {
            try {
                if (!peer.isClosed() && peer.isConnected()) {
                    PrintWriter out = new PrintWriter(peer.getOutputStream(), true);
                    out.println(message);
                } else {
                    connectedPeers.remove(peer);
                }
            } catch (IOException e) {
                System.err.println("❌ Failed to broadcast message to peer: " + peer.getInetAddress());
                connectedPeers.remove(peer);
            }
        }
    }

    public void broadcastTransaction(TX tx) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode message = mapper.createObjectNode();
            message.put("type", "transaction");
            message.set("payload", mapper.readTree(tx.createJSON()));
            String jsonMessage = mapper.writeValueAsString(message);
            broadcast(jsonMessage);
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast transaction:");
            e.printStackTrace();
        }
    }

    public static void broadcastTransactionStatic(TX tx) {
        if (instance != null) {
            instance.broadcastTransaction(tx);
        }
    }

    public static void broadcastBlock(Block block) {
        if (instance != null) {
            instance.instanceBroadcastBlock(block);
        }
    }

    private void instanceBroadcastBlock(Block block) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode message = mapper.createObjectNode();
            message.put("type", "block");
            message.set("payload", mapper.readTree(block.createJSON()));
            String blockMessage = mapper.writeValueAsString(message);
    
            for (Map.Entry<Socket, PeerInfo> entry : peers.entrySet()) {
                Socket socket = entry.getKey();
                PeerInfo info = entry.getValue();
    
                // ✅ Only send to FULL peers
                if (!"FULL".equalsIgnoreCase(info.getSyncMode())) {
                    // Optionally: System.out.println("Skipping TX_ONLY peer: " + info.getAddress());
                    continue;
                }
    
                if (!socket.isClosed() && socket.isConnected()) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(blockMessage);
                } else {
                    peers.remove(socket); // Clean up disconnected peer
                }
            }
    
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast block:");
            e.printStackTrace();
        }
    }

    public  List<String> getKnownPeers() {
        return knownAddresses;
    }

    public void connectToPeer(String host) {
        try {
            Socket socket = new Socket(host, port);
            connectedPeers.add(socket);
            knownAddresses.add(host + ":" + port);
            System.out.println("Connected to peer: " + host + ":" + port);
            sendHandshake(socket);
            new Thread(() -> handleIncomingMessages(socket)).start();
        } catch (IOException e) {
            System.err.println("Failed to connect to peer at " + host + ":" + port);
        }
    }

    private void sendHandshake(Socket socket) {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode handshake = mapper.createObjectNode();
            handshake.put("type", "handshake");
            handshake.put("blockHeight", blockchainDB.getHeight());
            handshake.put("requestSync", true);
            handshake.put("address", portal.admin.address);
            handshake.put("syncMode", syncMode);
            handshake.put("isValidator", true);
            out.println(mapper.writeValueAsString(handshake));
        } catch (IOException e) {
            System.err.println("Failed to send handshake");
        }
    }

    public static void registerPeer(Socket socket, PeerInfo info) {
        peers.put(socket, info);
    }

    public static Collection<PeerInfo> getConnectedPeers() {
        return peers.values();
    }

    public static List<PeerInfo> getActiveValidators() {
    return peers.values().stream()
        .filter(PeerInfo::getIsValidator)
        .collect(Collectors.toList());
    }

    public static void broadcastRejection(String txHash) {
        if (instance != null) {
            instance.broadcastRejectionInternal(txHash);
        }
    }

    private void broadcastRejectionInternal(String txHash) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode message = mapper.createObjectNode();
            message.put("type", "tx_rejected");
    
            ObjectNode payload = mapper.createObjectNode();
            payload.put("txHash", txHash);
    
            message.set("payload", payload);
            String jsonMessage = mapper.writeValueAsString(message);
    
            broadcast(jsonMessage);
            System.out.println("📣 Broadcasted rejection for TX: " + txHash);
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast rejection gossip:");
            e.printStackTrace();
        }
    }

}

