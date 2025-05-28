package RMIandSocket.nodes;

// nodes/Node.java

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class Node9001 {
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt("9001");
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Node started on port " + port);

        // Sync Nodes
        new Thread(() -> {
            while (true) {
                try {
                    System.out.println("[AUTO-SYNC THREAD] Running sync cycle...");

                    autoSyncFilesToOtherNodes("9001");

                    Thread.sleep(10_000); // wait 10 seconds before next sync

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        //--------------------------------------
        while (true) {
            Socket client = serverSocket.accept();
            new Thread(() -> handle(client)).start();
        }
    }



    public static void autoSyncFilesToOtherNodes(String currentNode) {
        String folderPath = "NodeStorage/";
        File folder = new File(folderPath);

        System.out.println(folder);

        if (!folder.exists()) {
            System.out.println("[AUTO-SYNC] Folder does not exist: " + folderPath);
            return;
        }

        File[] deptFolders = folder.listFiles(File::isDirectory); //  Only directories
        if (deptFolders == null || deptFolders.length == 0) {
            System.out.println("[AUTO-SYNC] No department folders to sync.");
            return;
        }

        // List of other nodes (excluding self)
        Map<String, Integer> otherNodes = new HashMap<>();
        otherNodes.put("9002", 9002);
        otherNodes.put("9003", 9003);

        for (Map.Entry<String, Integer> entry : otherNodes.entrySet()) {
            String targetNode = entry.getKey();
            int port = entry.getValue();

            for (File deptFolder : deptFolders) {
                File[] files = deptFolder.listFiles(File::isFile); //  Only real files
                if (files == null) continue;

                for (File file : files) {
                    try {
                        Socket socket = new Socket("localhost", port);
                        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                        FileInputStream fis = new FileInputStream(file);

                        // Send department + file name
                        String message = deptFolder.getName() + "|" + file.getName();
                        dos.writeUTF(message);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) > 0) {
                            dos.write(buffer, 0, bytesRead);
                        }

                        fis.close();
                        dos.close();
                        socket.close();

                        System.out.println("[AUTO-SYNC] Sent " + file.getName() + " to node " + targetNode);

                    } catch (Exception e) {
                        System.out.println("[AUTO-SYNC] Node " + targetNode + " is down. Skipping sync.");
                    }
                }
            }
        }
    }

    static void handle(Socket socket) {
        try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        ) {
            String msg = in.readUTF();
            String[] parts = msg.split("\\|");

            if (parts[0].equals("UPLOAD")) {
                String dept = parts[1];
                String filename = parts[2];
                String content = parts[3];

                File dir = new File("NodeStorage/" + dept);
                dir.mkdirs();

                try (FileWriter writer = new FileWriter(new File(dir, filename))) {
                    writer.write(content);
                }

            } else if (parts[0].equals("GET")) {
                String filename = parts[1];

                // نبحث داخل جميع المجلدات في NodeStorage
                File root = new File("NodeStorage");
                File[] departments = root.listFiles(File::isDirectory);

                boolean found = false;

                if (departments != null) {
                    for (File dept : departments) {
                        File file = new File(dept, filename);
                        if (file.exists()) {
                            String content = new String(Files.readAllBytes(file.toPath()));
                            out.writeUTF("From " + dept.getName() + ": " + content);
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    out.writeUTF("NOT_FOUND");
                }
            }
            else if (parts[0].equals("EDIT")) {
                String dept = parts[1];
                String filename = parts[2];
                String newContent = parts[3];

                File file = new File("NodeStorage/" + dept + "/" + filename);
                if (file.exists()) {
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(newContent);
                    }
                    out.writeUTF("File edited successfully.");
                } else {
                    out.writeUTF("File not found for editing.");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
