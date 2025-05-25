package RMIandSocket.Client;

// client/ClientApp.java


import RMIandSocket.common.AuthService;

import java.io.*;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

public class ClientApp11 {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        AuthService auth = (AuthService) registry.lookup("AuthService");

        // --------------------- New
        System.out.print("Login as (1) Admin or (2) Employee: ");
        int role = Integer.parseInt(scanner.nextLine());

        if (role == 1) {
            System.out.print("Admin Username: ");
            String adminUser = scanner.nextLine();
            System.out.print("Admin Password: ");
            String adminPass = scanner.nextLine();

            if (!auth.login(adminUser, adminPass)) {
                System.out.println("Admin login failed.");
                return;
            }

            System.out.print("New Employee Username: ");
            String newUser = scanner.nextLine();
            System.out.print("New Employee Password: ");
            String newPass = scanner.nextLine();
            System.out.print("Department (e.g., qa, graphics, development): ");
            String dept = scanner.nextLine();

            boolean success = auth.registerEmployee(adminUser, newUser, newPass, dept);
            if (success) {
                System.out.println("Employee registered successfully.");
            } else {
                System.out.println("Failed to register employee (maybe user already exists).");
            }

            return; // Exit after admin registers
        }
        // ---------------------------------------
        System.out.print("Username: ");
        String user = scanner.nextLine();

        System.out.print("Password: ");
        String pass = scanner.nextLine();

        if (!auth.login(user, pass)) {
            System.out.println("Login failed.");
            return;
        }

        String token = auth.generateToken(user);
        String dept = auth.getDepartment(user);

        System.out.println("Login success! Token: " + token + ", Dept: " + dept);

        while (true) {
            System.out.println("1. Upload File\n2. View File\n3. Edit File\n4. Exit");
            int choice = Integer.parseInt(scanner.nextLine());

            if (choice == 1) {
                System.out.print("Filename: ");
                String filename = scanner.nextLine();
                System.out.print("Content: ");
                String content = scanner.nextLine();

                try (Socket socket = new Socket("localhost", 8000);
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                     DataInputStream in = new DataInputStream(socket.getInputStream())) {
                    out.writeUTF("UPLOAD|" + dept + "|" + filename + "|" + content);
                    System.out.println(in.readUTF());
                }

            } else if (choice == 2) {
                System.out.print("Filename: ");
                String filename = scanner.nextLine();

                try (Socket socket = new Socket("localhost", 8000);
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                     DataInputStream in = new DataInputStream(socket.getInputStream())) {
                    out.writeUTF("GET|" + dept + "|" + filename);
                    System.out.println(in.readUTF());
                }

            } else if (choice == 3) {
                System.out.print("Filename to edit: ");
                String filename = scanner.nextLine();
                System.out.print("New content: ");
                String content = scanner.nextLine();

                try (Socket socket = new Socket("localhost", 8000);
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                     DataInputStream in = new DataInputStream(socket.getInputStream())) {

                    out.writeUTF("EDIT|" + dept + "|" + filename + "|" + content);
                    System.out.println(in.readUTF());
                }
            }
            else {
                break;
            }
        }
    }
}
