package RMIandSocket.nodes;


// nodes/Node.java

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

public class Node9002 {

    static String nodeId = "9002";

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt("9002");
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Node started on port " + port);

        // Sync Nodes
        new Thread(() -> {
            while (true) {
                try {
                    System.out.println("[AUTO-SYNC THREAD] Running sync cycle...");

                    autoSyncFilesToOtherNodes("9002");

                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime nextRun = now.toLocalDate().plusDays(1).atStartOfDay(); // بداية اليوم التالي

                    long millisToMidnight = Duration.between(now, nextRun).toMillis();

                    Thread.sleep(3600_000); // wait 10 seconds before next sync || or millisToMidnight to next day

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
        System.out.println("Thread Ended");

        //--------------------------------------

        while (true) {
            Socket client = serverSocket.accept();
            new Thread(() -> handle(client)).start();
        }
    }

    public static void autoSyncFilesToOtherNodes(String currentNode) {

        //String folderPath = "NodeStorage/";

        String folderPath = "Node" + currentNode + "Storage/";

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
        otherNodes.put("9001", 9001);
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
                        String message = "SYNC|" + deptFolder.getName() + "|" + file.getName();
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

            // Base folder for this node (important!)
            String baseFolder = "Node9002Storage/"; // ← غيّر هذا حسب رقم الـ Node الحالي

            // === حالة استلام ملف من autoSync (من Node آخر) ===
            if (parts[0].equals("SYNC") && parts.length == 3) {
                String dept = parts[1];
                String filename = parts[2];

                File dir = new File(baseFolder + dept);
                dir.mkdirs();

                File file = new File(dir, filename);

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) > 0) {
                        fos.write(buffer, 0, bytesRead);
                        if (bytesRead < 4096) break; // نهاية الملف
                    }
                }

                System.out.println("[SYNC] Received file: " + filename + " into " + baseFolder + dept);
                return; // لا تتابع باقي الشروط
            }



            // === حالة رفع ملف من المستخدم (عبر UPLOAD) ===
            if (parts[0].equals("UPLOAD")) {
                String dept = parts[1];
                String filename = parts[2];
                String content = parts[3];

                File dir = new File(baseFolder + dept);
                dir.mkdirs();

                try (FileWriter writer = new FileWriter(new File(dir, filename))) {
                    writer.write(content);
                }
                new Thread(() -> autoSyncFilesToOtherNodes(nodeId)).start();


            }  else if (parts[0].equals("GET")) {
                String filename = parts[1];

                // نبحث داخل جميع المجلدات في NodeStorage
                //File root = new File("NodeStorage");
               // File root = new File("Node9002Storage/");
                File root = new File("Node" + nodeId + "Storage/");

                System.out.println("GET :===================" + root + " File Name ---------" + filename);

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
            } else if (parts[0].equals("EDIT")) {
                String dept = parts[1];
                String filename = parts[2];
                String newContent = parts[3];

                File file = new File(baseFolder + dept + "/" + filename);
                if (file.exists()) {
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(newContent);
                    }
                    new Thread(() -> autoSyncFilesToOtherNodes(nodeId)).start();

                    out.writeUTF("File edited successfully.");
                } else {
                    out.writeUTF("File not found for editing.");
                }
            }

            else if (parts[0].equals("DELETE")) {
                String dept = parts[1];
                String filename = parts[2];
                File file = new File(baseFolder + dept + "/" + filename);
                if (file.exists() && file.delete()) {
                    out.writeUTF("DELETED");
                } else {
                    out.writeUTF("File not found or delete failed.");
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
