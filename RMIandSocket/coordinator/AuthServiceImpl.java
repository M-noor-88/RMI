package RMIandSocket.coordinator;




import RMIandSocket.common.AuthService;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.util.*;

public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {
    private Map<String, String> users = new HashMap<>(); // username → password
    private Map<String, String> departments = new HashMap<>(); // username → department
    private Map<String, String> tokens = new HashMap<>(); // username → token


    protected AuthServiceImpl() throws RemoteException {
        super();
        users.put("ali", "123");
        departments.put("ali", "graphics");

        users.put("sara", "123");
        departments.put("sara", "development");

        users.put("ahmad", "123");
        departments.put("ahmad", "qa");

        users.put("noor", "123");
        departments.put("noor", "qa");

        users.put("admin", "admin123");
        departments.put("admin", "management");

    }

    public boolean login(String username, String password) throws RemoteException {
        return users.containsKey(username) && users.get(username).equals(password);
    }

    public String generateToken(String username) throws RemoteException {
        String token = UUID.randomUUID().toString();
        tokens.put(username, token);
        return token;
    }

    public String getDepartment(String username) throws RemoteException {
        return departments.get(username);
    }

    // Add this new method:
    public boolean registerEmployee(String adminUsername, String employeeUsername, String password, String department) throws RemoteException {
        // Only allow admin to register new users
        if (!adminUsername.equals("admin")) {
            return false;
        }

        // Check if user already exists
        if (users.containsKey(employeeUsername)) {
            return false;
        }

        users.put(employeeUsername, password);
        departments.put(employeeUsername, department);
        return true;
    }
}