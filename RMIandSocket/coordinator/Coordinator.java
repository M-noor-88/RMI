package RMIandSocket.coordinator;

// coordinator/Coordinator.java

import java.io.*;
import java.net.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Coordinator {

    static int[] nodePorts = {9001, 9002, 9003};

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

    static void handleClient(Socket socket) {
        try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        ) {
            String cmd = in.readUTF(); // e.g. UPLOAD|department|filename|content
            String[] parts = cmd.split("\\|", 4);

            if (parts[0].equals("UPLOAD")) {
                // Choose a random node
                int nodePort = nodePorts[(int)(Math.random() * nodePorts.length)];
                Socket nodeSocket = new Socket("localhost", nodePort);
                DataOutputStream nodeOut = new DataOutputStream(nodeSocket.getOutputStream());
                nodeOut.writeUTF(cmd);
                nodeOut.close();
                nodeSocket.close();
                out.writeUTF("File sent to node " + nodePort);
            } else if (parts[0].equals("GET")) {
                // Search in all nodes
                String filename = parts[2];
                for (int port : nodePorts) {
                    try (Socket nodeSocket = new Socket("localhost", port)) {
                        DataOutputStream nodeOut = new DataOutputStream(nodeSocket.getOutputStream());
                        DataInputStream nodeIn = new DataInputStream(nodeSocket.getInputStream());
                        //nodeOut.writeUTF(cmd); // GET|department|filename
                        nodeOut.writeUTF("GET|" + filename); // <-- no department
                        String response = nodeIn.readUTF();
                        if (!response.equals("NOT_FOUND")) {
                            out.writeUTF("From Node " + port + ": " + response);
                            return;
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }
                out.writeUTF("File not found in any node.");
            }
            else if (parts[0].equals("EDIT")) {
                // Choose a random node (same as upload)
                int nodePort = nodePorts[(int)(Math.random() * nodePorts.length)];
                Socket nodeSocket = new Socket("localhost", nodePort);
                DataOutputStream nodeOut = new DataOutputStream(nodeSocket.getOutputStream());
                DataInputStream nodeIn = new DataInputStream(nodeSocket.getInputStream());

                nodeOut.writeUTF(cmd); // full EDIT|dept|filename|newcontent
                String response = nodeIn.readUTF(); // get success/failure
                out.writeUTF(response);

                nodeOut.close();
                nodeSocket.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
