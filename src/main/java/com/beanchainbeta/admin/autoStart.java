package com.beanchainbeta.admin;

import org.springframework.boot.SpringApplication;

import com.beanchainbeta.BeanChainApi;
import com.beanchainbeta.Validation.TimerFunc;
import com.beanchainbeta.config.ConfigLoader;
import com.beanchainbeta.nodePortal.portal;
import com.beanchainbeta.services.CleanupService;
import com.bean_core.Wizard.*;

public class autoStart {
    

    public static void nodeStart() throws Exception {
        
        
        System.out.println("🫘 BeanChain Node Initializing...");
        System.out.println("▶ IP : " + ConfigLoader.bindAddress);
    

        boolean signedIn = false;
        while (!signedIn) {
            try {
                adminCube admin = new adminCube(wizard.wizardRead(ConfigLoader.privateKeyPath), ConfigLoader.bindAddress);
                admin.signedIn = true;
                portal.admin = admin;
                signInSuccess();
                signedIn = true;
            } catch (Exception e) {
                System.out.println("SIGN IN FAILED: " + e.getMessage());
                Thread.sleep(3000); // pause before retrying
            }
        }
    }

    private static void signInSuccess(){
        Thread springThread = new Thread(() -> {
                    SpringApplication.run(BeanChainApi.class);
                }, "SpringThread");

        springThread.setDaemon(false);
        springThread.start();
        System.out.println("SIGN IN SUCCESS");

        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.print("\033[H\033[2J");  
        System.out.flush();
        System.out.println("\u001B[32m" + prompt.logo + "\u001B[0m"); 
        TimerFunc.nodeFleccer();
        new Thread(() -> {
            while (true) {
                try {
                    CleanupService.runFullCleanup();
                    Thread.sleep(6 * 60 * 60 * 1000); // Sleep 6 hours
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
