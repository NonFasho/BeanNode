package com.beanchainbeta.validation;

import com.beanchainbeta.network.Node;
import com.beanchainbeta.services.RejectedService;
import com.beanchainbeta.services.WalletService;
import com.bean_core.TXs.*;
import com.bean_core.crypto.*;
import com.bean_core.Utils.*;

public class TxVerifier {

    //runs a lot of boolean checks to decide if the transaction is valid and can be added to a new block 
    public static boolean verrifyTransaction(TX tx) throws Exception{
        //debug
        //this.debugHashValues();
        //end-debug
        if (tx.getSignature() != null && tx.getSignature().equals("GENESIS-SIGNATURE")) {
            //System.out.println("🪙 System TX accepted without signature verification: " + txHash);
            return true;
        }

        boolean hasAddy = (tx.getFrom() !=null);
        //System.out.println("HasAddy" + (hasAddy));
        boolean hasSignature = (tx.getSignature() !=null);
        //System.out.println("HasSignature " + (hasSignature));
        //System.out.println(this.txHash + " vs gen: " + this.generateHash());
        boolean correctHash = (tx.getTxHash().equals(tx.generateHash()));
        //System.out.println("CorrectHash " + (correctHash));
        //System.out.println("CorrectNonce " + (this.nonce == WalletService.getNonce(from)));
        //boolean correctNonce = (this.nonce == (WalletService.getNonce(from)));
        boolean addyMatch = false;
        boolean validOwner = false;
        boolean senderHasEnough = false;

        if(hasAddy && hasSignature && correctHash) {
            addyMatch = TransactionVerifier.walletMatch(tx.getPublicKeyHex(), tx.getFrom());
            //System.out.println("addymatch: " + addyMatch);
            validOwner = TransactionVerifier.verifySHA256Transaction(tx.getPublicKeyHex(), hex.hexToBytes(tx.getTxHash()), tx.getSignature());
            //System.out.println("validowner: " + validOwner);
            senderHasEnough = WalletService.hasCorrectAmount(tx.getFrom(), tx.getAmount(), tx.getGasFee());
            //System.out.println("sender has enough: " + senderHasEnough);
            if(addyMatch && validOwner && senderHasEnough) {
                return true;
            } else {
                System.out.println("** TX FAILED: " + tx.getTxHash() + " VERIFICATION FAILURE **");
                tx.setStatus("rejected");
                RejectedService.saveRejectedTransaction(tx);
                Node.broadcastRejection(tx.getTxHash());
                return false;
            }

        } else {
            System.out.println("** TX FAILED: " + tx.getTxHash() + " INFO MISMATCH **");
            tx.setStatus("rejected");
            RejectedService.saveRejectedTransaction(tx);
            Node.broadcastRejection(tx.getTxHash());
            return false;

        }
    }

    public static boolean lightSyncVerify(TX tx) throws Exception {
        if (tx.getSignature() != null && tx.getSignature().equals("GENESIS-SIGNATURE")) {
            //System.out.println("🪙 System TX accepted without signature verification: " + txHash);
            return true;
        }
        boolean hasAddy = (tx.getFrom() != null);
        boolean hasSignature = (tx.getSignature() != null);
        boolean correctHash = (tx.getTxHash().equals(tx.generateHash()));
        //boolean correctNonce = (this.nonce == WalletService.getNonce(from));
    
        if (hasAddy && hasSignature && correctHash) {
            boolean addyMatch = TransactionVerifier.walletMatch(tx.getPublicKeyHex(), tx.getFrom());
            boolean validOwner = TransactionVerifier.verifySHA256Transaction(tx.getPublicKeyHex(), hex.hexToBytes(tx.getTxHash()), tx.getSignature());
            boolean senderHasEnough = WalletService.hasCorrectAmount(tx.getFrom(), tx.getAmount(), tx.getGasFee());
    
            if (addyMatch && validOwner && senderHasEnough) {
                return true; 
            } else {
                System.err.println("❌ lightSyncVerify failed: " + tx.getTxHash());
                return false;
            }
        } else {
            System.err.println("❌ lightSyncVerify failed basic fields: " + tx.getTxHash());
            return false;
        }
    }

}
