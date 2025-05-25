package RMIandSocket.nodes;

// nodes/Node.java

import java.io.*;
import java.net.*;
import java.nio.file.Files;

public class Node9001 {
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt("9001");
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Node started on port " + port);

        while (true) {
            Socket client = serverSocket.accept();
            new Thread(() -> handle(client)).start();
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
