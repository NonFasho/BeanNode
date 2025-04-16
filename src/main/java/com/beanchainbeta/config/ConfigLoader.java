package com.beanchainbeta.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigLoader {
    public static String privateKeyPath;
    public static String bindAddress;
    public static int networkPort;
    public static boolean isBootstrapNode;
    public static String bootstrapIp;
    public static boolean isPublicNode;

    public static void loadConfig() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("config.docs/beanchain.config.properties")) {
            props.load(fis);

            privateKeyPath = props.getProperty("privateKeyPath", "config.docs/wizkey.txt");
            bindAddress = props.getProperty("bindAddress", "0.0.0.0");
            networkPort = Integer.parseInt(props.getProperty("networkPort", "6442"));
            isBootstrapNode = Boolean.parseBoolean(props.getProperty("isBootstrapNode", "false"));
            isPublicNode = Boolean.parseBoolean(props.getProperty("isPublicNode", "false"));
            bootstrapIp = props.getProperty("bootstrapIp", "65.38.97.169");


        } catch (IOException e) {
            System.err.println("⚠️ Failed to load BeanChain config: " + e.getMessage());
            System.exit(1);
        }
    }
}


