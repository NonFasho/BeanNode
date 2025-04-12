package com.beanchainbeta.admin;

// import java.util.Scanner;
// import org.springframework.boot.SpringApplication;
// import com.beanchainbeta.BeanChainApi;
// import com.beanchainbeta.Validation.TimerFunc;
import com.beanchainbeta.nodePortal.portal;

public class prompt {
    public static String logo = 
        "_____________________   _____    _______  _________   ___ ___    _____  .___ _______\n" + 
        "\\______   \\_   _____/  /  _  \\   \\      \\ \\_   ___ \\ /   |   \\  /  _  \\ |   |\\      \\\n" +  
        " |    |  _/|    __)_  /  /_\\  \\  /   |   \\/    \\  \\//    ~    \\/  /_\\  \\|   |/   |   \\\n" + 
        " |    |   \\|        \\/    |    \\/    |    \\     \\___\\    Y    /    |    \\   /    |    \\\n" +
        " |______  /_______  /\\____|__  /\\____|__  /\\______  /\\___|_  /\\____|__  /___\\____|__  /\n" +
        "         \\/        \\/         \\/         \\/        \\/      \\/         \\/            \\/\n" +
        "                            B E A N C H A I N::" + portal.currentVersion;

//     public static void nodeStart() throws Exception {
//         Scanner scanner = new Scanner(System.in);
//         System.out.println("Welcome to BEANCHAIN::" + portal.currentVersion);
//         System.out.print("PRIVATE KEY:");
//         String adminKey = scanner.nextLine();
//         System.out.print("PUBLIC IP:");
//         String ip = scanner.nextLine();

//         try {
//             adminCube admin = new adminCube(adminKey, ip);
//             admin.signedIn = true;
//             portal.admin = admin;
//             signInSuccess();
//         } catch (Exception e) {
//             System.out.println("SIGN IN FAILED" + e.getMessage());
//             scanner.close();
//             nodeStart();
//         }

//         scanner.close();
//     }

//     private static void signInSuccess(){
//         Thread springThread = new Thread(() -> {
//                     SpringApplication.run(BeanChainApi.class);
//                 }, "SpringThread");

//         springThread.setDaemon(false);
//         springThread.start();
//         System.out.println("SIGN IN SUCCESS");

//         try {
//             Thread.sleep(4000);
//         } catch (InterruptedException e) {
//             e.printStackTrace();
//         }

//         System.out.print("\033[H\033[2J");  
//         System.out.flush();
//         System.out.println(logo); 
//         TimerFunc.nodeFleccer();
//     }
    
}
