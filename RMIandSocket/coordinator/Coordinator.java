package RMIandSocket.coordinator;

// coordinator/Coordinator.java

import java.io.*;
import java.net.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.*;

public class Coordinator {

    static int[] nodePorts = {9001, 9002, 9003};

    // load balance
    static int editCounter = 0;



    public static void main(String[] args) throws Exception {
        // Run RMI Registry
        AuthServiceImpl auth = new AuthServiceImpl();
        Registry registry = LocateRegistry.createRegistry(1099);
        registry.rebind("AuthService", auth);
        System.out.println("RMI AuthService bound");

        // Listen for file upload requests
        ServerSocket serverSocket = new ServerSocket(8000);
        System.out.println("Coordinator (Socket Router) started");

        while (true) {
            Socket client = serverSocket.accept();
            new Thread(() -> handleClient(client)).start();
        }
    }


    // Search parallel , load Balance
    static String parallelSearchFile(String filename) {
        ExecutorService executor = Executors.newFixedThreadPool(nodePorts.length);
        CompletionService<String> completionService = new ExecutorCompletionService<>(executor);


        for (int port : nodePorts) {
            int currentPort = port;
            completionService.submit(() -> {
                try (
                        Socket nodeSocket = new Socket("localhost", currentPort);
                        DataOutputStream nodeOut = new DataOutputStream(nodeSocket.getOutputStream());
                        DataInputStream nodeIn = new DataInputStream(nodeSocket.getInputStream());
                ) {
                    System.out.println("Trying node " + currentPort + " for file " + filename);

                    nodeOut.writeUTF("GET|" + filename);
                    String response = nodeIn.readUTF();
                    if (!response.equals("NOT_FOUND")) {
                        return "From Node " + currentPort + ": " + response;
                    }
                } catch (IOException e) {
                    return null;
                }
                return null;
            });
        }

        try {
            for (int i = 0; i < nodePorts.length; i++) {
                Future<String> future = completionService.take(); // blocks for next completed task
                String result = future.get();
                if (result != null) {
                    executor.shutdownNow(); // cancel others
                    return result;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        executor.shutdownNow(); // cleanup
        return "File not found in any node.";
    }

    //-------------------------------------------------------

    // load balance  , for edit  , last worked node
    static synchronized int getNextNodePortForEdit() {
        int port = nodePorts[editCounter];
        System.out.println("[DEBUG] getNextNodePortForEdit called - Returning last worked node | port: " + port );

        editCounter = (editCounter + 1) % nodePorts.length;
        return port;
    }
    // -------------------------------
    static void handleClient(Socket socket) {
        try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        ) {
            String cmd = in.readUTF(); // e.g. UPLOAD|department|filename|content
            String[] parts = cmd.split("\\|", 4);

            if (parts[0].equals("UPLOAD")) {

                // Fail over , with load balance

                boolean success = false;
                int attempts = 0;

                while (!success && attempts < nodePorts.length) {
                    // load balance
                    int nodePort = getNextNodePortForEdit();
                    try (
                            Socket nodeSocket = new Socket("localhost", nodePort);
                            DataOutputStream nodeOut = new DataOutputStream(nodeSocket.getOutputStream());
                    ) {
                        nodeOut.writeUTF(cmd);
                        out.writeUTF("File sent to node " + nodePort);
                        success = true;
                    } catch (IOException e) {
                        System.out.println("[Failover] Node " + nodePort + " is down. Trying next...");
                        attempts++;
                    }
                }

                if (!success) {
                    out.writeUTF("Upload failed. All nodes are down.");
                }

            } else if (parts[0].equals("GET")) {
                // Search in all nodes
                String filename = parts[2];

                // Search parallel
                System.out.println("[Coordinator] Searching for file: " + filename);
                String result = parallelSearchFile(filename); // ✅ actual call to the method
                System.out.println("[Coordinator] Search result: " + result);
                out.writeUTF(result);

            }
            else if (parts[0].equals("EDIT")) {

                // Fail over and load balance
                boolean success = false;
                int attempts = 0;

                while (!success && attempts < nodePorts.length) {
                    // load balance
                    int nodePort = getNextNodePortForEdit();
                    try (
                            Socket nodeSocket = new Socket("localhost", nodePort);
                            DataOutputStream nodeOut = new DataOutputStream(nodeSocket.getOutputStream());
                            DataInputStream nodeIn = new DataInputStream(nodeSocket.getInputStream());
                    ) {
                        nodeOut.writeUTF(cmd);
                        String response = nodeIn.readUTF();
                        out.writeUTF(response);
                        success = true;
                    } catch (IOException e) {
                        System.out.println("[Failover] Node " + nodePort + " is down, trying next...");
                        attempts++;
                    }
                }

                if (!success) {
                    out.writeUTF("All nodes are down. Operation failed.");
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
