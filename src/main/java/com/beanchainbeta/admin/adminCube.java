package com.beanchainbeta.admin;

import java.security.PrivateKey;

import com.beanchainbeta.tools.Node;
import com.beanchainbeta.tools.WalletGenerator;

public class adminCube {
    public String privateKeyHex;
    private PrivateKey privateKey;
    public String publicKeyHex;
    public String address;
    public String nodeIp;
    public boolean signedIn = false;
    
    public adminCube(String privateKeyHex, String publicIp) throws Exception{
        this.privateKeyHex = privateKeyHex;
        this.privateKey = WalletGenerator.restorePrivateKey(privateKeyHex);
        publicKeyHex = WalletGenerator.generatePublicKey(privateKey);
        address = WalletGenerator.generateAddress(publicKeyHex);
        nodeIp = publicIp;
        Node node = new Node(nodeIp);

        Thread nodeThread = new Thread(() -> {
               node.start();
        }, "NodeThread");
        nodeThread.setDaemon(false);
        nodeThread.start();

        //TEST TEST TEST//
        System.out.println("Private Key: " + privateKeyHex);
        System.out.println("Public Key: " + publicKeyHex);
        System.out.println("Node IP: " + nodeIp);
        //END TEST END TEST END TEST//

    }

    public static void main(String[] args) throws Exception {
        

    }
}
