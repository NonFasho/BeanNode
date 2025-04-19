package com.beanchainbeta.validation;

import com.bean_core.TXs.TX;
import com.bean_core.crypto.TransactionVerifier;
import com.beanchainbeta.network.Node;
import com.beanchainbeta.services.RejectedService;
import com.beanchainbeta.services.WalletService;

public class MintVerifier {

    public static boolean verifyTransaction() {
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
    
}
