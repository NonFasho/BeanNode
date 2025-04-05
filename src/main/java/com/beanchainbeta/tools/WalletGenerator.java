package com.beanchainbeta.tools;


import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.*;
import java.util.HashMap;
import java.util.Map;
import java.security.MessageDigest;

public class WalletGenerator {

    static {
        Security.addProvider(new BouncyCastleProvider()); // Ensure Bouncy Castle is available
    }

    private static final Map<String, String> addressToPublicKeyMap = new HashMap<>();

    public static String generatePrivateKey() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256k1");
        SecureRandom secureRandom = new SecureRandom();  // Ensuring strong randomness
        keyPairGenerator.initialize(ecSpec, secureRandom);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivate();
        return String.format("%064x", privateKey.getS());  // Ensuring 64-character format
    }

    public static PrivateKey restorePrivateKey(String hexPrivateKey) throws Exception {
        if (hexPrivateKey.length() != 64) {
            throw new IllegalArgumentException("Invalid private key length. Expected 64-character hex string.");
        }

        BigInteger privateKeyValue = new BigInteger(hexPrivateKey, 16);
        ECNamedCurveParameterSpec bcSpec = ECNamedCurveTable.getParameterSpec("secp256k1");
        org.bouncycastle.jce.spec.ECPrivateKeySpec privateKeySpec = new org.bouncycastle.jce.spec.ECPrivateKeySpec(privateKeyValue, bcSpec);

        KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");
        return keyFactory.generatePrivate(privateKeySpec);
    }

    public static String generatePublicKey(PrivateKey privateKey) throws Exception {
        ECPrivateKey ecPrivateKey = (ECPrivateKey) privateKey;
        BigInteger privateKeyValue = ecPrivateKey.getS();
        ECNamedCurveParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("secp256k1");
        ECPoint G = ecSpec.getG();
        ECPoint publicPoint = G.multiply(privateKeyValue).normalize();
    
        if (publicPoint.isInfinity()) {
            throw new IllegalArgumentException("Invalid public key: Point at infinity.");
        }
    
        return "04" + String.format("%064x", publicPoint.getXCoord().toBigInteger()) +
                      String.format("%064x", publicPoint.getYCoord().toBigInteger());
    }

    public static String generateAddress(String publicKeyHex) throws Exception {
        if (!publicKeyHex.startsWith("04") || publicKeyHex.length() != 130) {
            throw new IllegalArgumentException("Invalid public key format. Expected uncompressed 130-character key starting with 04.");
        }

        byte[] publicKeyBytes = hexToBytes(publicKeyHex);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] sha256Hash = sha256.digest(publicKeyBytes);

        MessageDigest ripemd160 = MessageDigest.getInstance("RIPEMD160", "BC");
        byte[] ripemd160Hash = ripemd160.digest(sha256Hash);

        String address = bytesToHex(ripemd160Hash);
        addressToPublicKeyMap.put("BEANX:0x" + address, publicKeyHex); // Store the public key mapping
        return "BEANX:0x" + address;
    }

    public static String getPublicKeyFromAddress(String address) {
        return addressToPublicKeyMap.getOrDefault(address, null);
    }

    private static byte[] hexToBytes(String hex) {
        int length = hex.length();
        byte[] data = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}

