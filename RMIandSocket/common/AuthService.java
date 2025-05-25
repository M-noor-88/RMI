package RMIandSocket.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AuthService extends Remote {
    boolean login(String username, String password) throws RemoteException;
    String generateToken(String username) throws RemoteException;
    String getDepartment(String username) throws RemoteException;

    boolean registerEmployee(String adminUsername, String employeeUsername, String password, String department) throws RemoteException;

}