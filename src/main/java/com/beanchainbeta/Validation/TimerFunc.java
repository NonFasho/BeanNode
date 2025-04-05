package com.beanchainbeta.Validation;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.beanchainbeta.admin.prompt;
import com.beanchainbeta.nodePortal.portal;


public class TimerFunc {

    private static final AtomicInteger counter = new AtomicInteger(0);

    public static void nodeFleccer(){
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        Runnable task = () -> {
            try {
                int count = counter.incrementAndGet();
                BlockBuilder.blockMaker(portal.admin.privateKeyHex); 

                if(count % 10 == 0) {
                    System.out.println(prompt.logo);
                }
            } catch (Exception e) {
                System.err.println("Error in blockMaker: " + e.getMessage());
                e.printStackTrace();
            }
        };
        scheduler.scheduleAtFixedRate(task, 1, 1, TimeUnit.MINUTES);
        System.out.println("[nodeFleccer] Block timer started.");
    }

    
    
}
