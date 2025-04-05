package com.beanchainbeta.config;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class SecuritySetup {
    public static void run() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
            //System.out.println("Bouncy Castle Provider Added!");
        } else {
            //System.out.println("Bouncy Castle Already Registered.");
        }   
    }
}
