package com.beanchainbeta.controllers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;


import com.beanchainbeta.TXs.TX;
import com.beanchainbeta.services.MempoolService;
import com.beanchainbeta.services.WalletService;
import com.beanchainbeta.services.blockchainDB;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class DBController {
    private final blockchainDB db;
    private final MempoolService mempoolService;
    private final WalletService walletService;

    public DBController(blockchainDB db, MempoolService mempool, WalletService walletService){
        this.db = db;
        this.mempoolService = mempool;
        this.walletService = walletService;
    }

    @PostMapping("/submit-transaction")
    public ResponseEntity<?> submitTransaction(@RequestBody Map<String, String> request) {
        String txHash = request.get("txHash");
        String transactionJson = request.get("transactionJson");

        System.out.println("txHash: " + txHash);
        System.out.println("transactionJson (string): " + transactionJson);

        if (txHash == null || transactionJson == null) {
            return ResponseEntity.badRequest().body("{\"status\": \"error\", \"message\": \"Missing txHash or transactionJson\"}");
        }

        TX tx;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            tx = objectMapper.readValue(transactionJson, TX.class);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("{\"status\": \"error\", \"message\": \"Invalid transaction JSON\"}");
        }

        // Optional: Validate the reconstructed TX if needed
        //System.out.println("✅ TX Object: " + tx.getFrom() + " → " + tx.getTo());

        if (mempoolService.addTransaction(txHash, transactionJson)) {
            return ResponseEntity.ok("{\"status\": \"success\", \"txHash\": \"" + txHash + "\"}");
        } else {
            return ResponseEntity.badRequest().body("{\"status\": \"error\", \"message\": \"Transaction rejected or already exists\"}");
        }
    }


    @PostMapping("/balance")
    public ResponseEntity<?> getBalance(@RequestBody Map<String, String> request) {
        String address = request.get("address");

        if (address == null || address.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Missing or empty address"
            ));
        }

        try {
            
            double balance = WalletService.getBeanBalance(address);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "address", address,
                "balance", balance
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Failed to retrieve balance",
                "details", e.getMessage()
            ));
        }
    }


    @GetMapping("/mempool")
    public ResponseEntity<?> getMempool() {
        return ResponseEntity.ok(mempoolService.getTransactions());
    }

    @GetMapping("/rejected/{address}")
    public ResponseEntity<?> getRejected(@PathVariable String address) {
        try {
            ConcurrentHashMap<String, String> rejected = MempoolService.getRejectedTransactions(address);
            
            //** TEST TEST TEST */
            //System.out.println("📥 Rejected TXs for " + address + ": " + rejected.size());
            rejected.forEach((hash, json) -> System.out.println(" - " + hash + ": " + json));
            //**TEST END TEST END */

            return ResponseEntity
                .ok()
                .contentType(APPLICATION_JSON)
                .body(rejected);
        } catch (Exception e) {
            System.err.println("ERROR FETCHING REJECTED" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body("Failed to fetch rejected transactions.");
        }
        
    }

    @GetMapping("/nonce/{address}")
    public ResponseEntity<?> getNonce(@PathVariable String address) {
        try {
            int nonce = WalletService.getNonce(address); 
            return ResponseEntity.ok(Collections.singletonMap("nonce", nonce));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @GetMapping("/txs/sent/{address}")
    public ResponseEntity<List<TX>> getSentTxs(@PathVariable String address) {
        List<TX> sentTxs = blockchainDB.getWalletCompleteTXs(address);
        return ResponseEntity.ok(sentTxs);
    }

    @GetMapping("txs/received/{address}")
    public ResponseEntity<List<TX>> getRecievedTxs(@PathVariable String address) {
        List<TX> receivedTxs = blockchainDB.getWalletInTXs(address);
        return ResponseEntity.ok(receivedTxs);
    }

    @GetMapping("/mempool/pending/{address}")
    public ResponseEntity<List<TX>> getPending(@PathVariable String address) {
        List<TX> pending = MempoolService.getPending(address);
        return ResponseEntity.ok(pending);
    }

    
}
