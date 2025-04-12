package com.beanchainbeta.nodePortal;

//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.Comparator;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.beanchainbeta.admin.adminCube;
import com.beanchainbeta.admin.autoStart;
import com.beanchainbeta.admin.autoStartPrivate;
import com.beanchainbeta.config.ConfigLoader;
//import com.beanchainbeta.admin.prompt;
import com.beanchainbeta.services.blockchainDB;

@SpringBootApplication
public class portal {
    public static final String currentVersion = "(BETA)";
    public static adminCube admin;
    public static blockchainDB beanchainTest = new blockchainDB();
    public static volatile boolean isSyncing = true;
    public static final long BOOT_TIME = System.currentTimeMillis();


    public static void setIsSyncing(boolean bool) {isSyncing = bool;}


    public static void main(String[] args) throws Exception {
        ConfigLoader.loadConfig();

        if(ConfigLoader.isBootstrapNode) {
            autoStart.nodeStart();
            setIsSyncing(false);
        } else {
            autoStartPrivate.nodeStart();
        }

    }
    
}
