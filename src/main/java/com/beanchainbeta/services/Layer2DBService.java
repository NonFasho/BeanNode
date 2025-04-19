package com.beanchainbeta.services;

import java.nio.charset.StandardCharsets;

import com.beanchainbeta.controllers.DBManager;
import com.bean_core.WalletModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.iq80.leveldb.DB;
import org.springframework.stereotype.Service;

@Service
public class Layer2DBService {
    private static final DB db = DBManager.getDB("tokenDB");
    private static final ObjectMapper mapper = new ObjectMapper();

    // 🔍 Check if wallet exists
    public static boolean walletExists(String address) {
        try {
            byte[] value = db.get(address.getBytes(StandardCharsets.UTF_8));
            return value != null;
        } catch (Exception e) {
            System.err.println("⚠️ Error checking wallet existence: " + address);
            e.printStackTrace();
            return false;
        }
    }

    // 📤 Load wallet
    public static Layer2Wallet loadWallet(String address) {
        try {
            byte[] value = db.get(address.getBytes(StandardCharsets.UTF_8));
            if (value != null) {
                String json = new String(value, StandardCharsets.UTF_8);
                return mapper.readValue(json, Layer2Wallet.class);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error loading wallet: " + address);
            e.printStackTrace();
        }
        return null;
    }

    // 💾 Save wallet
    public static void saveWallet(Layer2Wallet wallet) {
        try {
            String json = mapper.writeValueAsString(wallet);
            db.put(wallet.getAddress().getBytes(StandardCharsets.UTF_8), json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("❌ Error saving wallet: " + wallet.getAddress());
            e.printStackTrace();
        }
    }

    // 🚀 Create or load wallet
    public static Layer2Wallet getOrCreateWallet(String address) {
        Layer2Wallet wallet = loadWallet(address);
        if (wallet == null) {
            wallet = new Layer2Wallet(address);
            saveWallet(wallet);
        }
        return wallet;
    }

    // 📥 Get token balance
    public static int getTokenBalance(String address, String token) {
        Layer2Wallet wallet = getOrCreateWallet(address);
        return wallet.getBalance(token);
    }

    // 🔁 Increment L2 nonce
    public static void incrementNonce(String address) {
        Layer2Wallet wallet = getOrCreateWallet(address);
        wallet.incrementNonce();
        saveWallet(wallet);
    }

    // 💸 Transfer token between wallets
    public static boolean transferToken(String fromAddress, String toAddress, String token, int amount) {
        try {
            Layer2Wallet from = getOrCreateWallet(fromAddress);
            Layer2Wallet to = getOrCreateWallet(toAddress);

            int fromBalance = from.getBalance(token);
            if (fromBalance < amount) {
                System.err.println("❌ Insufficient balance for transfer: " + token + " | " + fromAddress);
                return false;
            }

            from.adjustBalance(token, -amount);
            to.adjustBalance(token, amount);
            from.incrementNonce();

            saveWallet(from);
            saveWallet(to);

            return true;
        } catch (Exception e) {
            System.err.println("❌ Transfer failed from " + fromAddress + " to " + toAddress + " | " + token);
            e.printStackTrace();
            return false;
        }
    }
}
