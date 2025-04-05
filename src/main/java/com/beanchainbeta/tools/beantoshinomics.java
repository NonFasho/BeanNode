package com.beanchainbeta.tools;

public class beantoshinomics {
    private static final int DECIMALS = 6;

    public static long toBeantoshi(double amount) {
        return(long)(amount * Math.pow(10,DECIMALS));
    }

    public static double toBean(long beantoshiAmount) {
        return beantoshiAmount / Math.pow(10, DECIMALS);
    }

}
