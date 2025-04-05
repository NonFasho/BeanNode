package com.beanchainbeta.wizard;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;

public class wizard {
    static String path = "D:/WIZPK/wizard.txt";

    public static void saveKeyToWizard(String privateHash) {
        String wizardString = numberChainGenerator(42) + "::" + numberChainGenerator(4) + privateHash + numberChainGenerator(4) + "::" + numberChainGenerator(24);
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write(wizardString);
        } catch (IOException e) {

        }

    }

    public static String wizardRead() throws IOException{
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String wizardKey = reader.readLine();
        String[] parts = wizardKey.split("::");
        String middieRib = parts[1];
        String retrievedHash = middieRib.substring(4, middieRib.length() - 4);
        return retrievedHash;
    }

    
    public static String numberChainGenerator(int length) {
        Random random = new Random();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < length; i++) {
        digits.append(random.nextInt(10));
        }
        return digits.toString();
    }

    public static String wizardCheck() throws IOException{
        File file = new File(path);
        if (file.exists()) {
            System.out.println("File exists!");
            return (wizardRead());
        } else {
            System.out.println("File does not exist.");
            return null;
        }
        

    }

    //testing
    //
    //

    // public static void wizCheckSignIn() throws IOException {
    //     String privateHash = (wizardCheck());
    //     //fix later 
    //     NodeBeta.currentPrivateHash = privateHash;
        
    // }

    
    
    public static void main(String[] args) throws Exception {

        System.out.println(wizardCheck());
    }
    
    
}
