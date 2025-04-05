package com.beanchainbeta.nodePortal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.beanchainbeta.admin.adminCube;
import com.beanchainbeta.admin.prompt;
import com.beanchainbeta.services.blockchainDB;

@SpringBootApplication
public class portal {
    public static final String currentVersion = "(BETA)";
    public static adminCube admin;
    public static blockchainDB beanchainTest = new blockchainDB();

    public static void main(String[] args) throws Exception {
        prompt.nodeStart();

    }

    public static void resetDatabases() {
    deleteFolder("BeanChainDBTest");
    deleteFolder("mempool_db");
    deleteFolder("stateDB");
    System.out.println("✅ All node databases reset.");
}

    private static void deleteFolder(String folderName) {
        Path path = Paths.get(folderName);
        try {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException e) {
            System.err.println("Failed to delete folder " + folderName + ": " + e.getMessage());
        }
    }
    
}
