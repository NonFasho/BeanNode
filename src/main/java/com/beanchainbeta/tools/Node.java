package com.beanchainbeta.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.beanchainbeta.controllers.MessageRouter;
import com.beanchainbeta.services.blockchainDB;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Node {
    private final int port = 6442;
    private String ip;
    private final ServerSocket serverSocket;
    private final Set<Socket> connectedPeers = ConcurrentHashMap.newKeySet();
    private final List<String>  knownAddresses = new CopyOnWriteArrayList<>();

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
        while(true) {
            try {
                Socket peer = serverSocket.accept();
                connectedPeers.add(peer);
                System.out.println("New peer connected: " + peer.getInetAddress().getHostAddress());

                new Thread(() -> handleIncomingMessages(peer)).start();
            } catch (IOException e){
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
            System.out.println("Message from " + peer.getInetAddress() + ": " + line);

            try {
                JsonNode message = mapper.readTree(line);
                router.route(message, peer);

            } catch (Exception e) {
                System.err.println("Failed to parse incoming JSON: " + line);
                e.printStackTrace();
            }
        }

    } catch (IOException e) {
        System.out.println("Connection lost with peer: " + peer.getInetAddress());
        connectedPeers.remove(peer);
    }
}

    public void broadcast(String message) {
        for (Socket peer : connectedPeers) {
            try {
                PrintWriter out = new PrintWriter(peer.getOutputStream(), true);
                out.println(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public List<String> getKnownPeers() {
        return knownAddresses;
    }

    public void connectToPeer(String host) {
        try {
            Socket socket = new Socket(host, port);
    
            // Add to peer set
            connectedPeers.add(socket);
            knownAddresses.add(host + ":" + port);
    
            System.out.println("Connected to peer: " + host + ":" + port);
    
            // Optionally send handshake
            sendHandshake(socket);
    
            // Start message handler thread
            new Thread(() -> handleIncomingMessages(socket)).start();
    
        } catch (IOException e) {
            System.err.println("Failed to connect to peer at " + host + ":" + port);
            e.printStackTrace();
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
            // add mempool sync + trans sync 

            //test 
            out.println(mapper.writeValueAsString(handshake));
            //end test 
        } catch (IOException e) {
            System.err.println("Failed to send handshake");
            e.printStackTrace();
        }
    }
}
