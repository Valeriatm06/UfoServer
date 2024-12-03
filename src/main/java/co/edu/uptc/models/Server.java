package co.edu.uptc.models;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import co.edu.uptc.utilities.ClientHandler;

public class Server {

    private ServerSocket serverSocket;
    private int port;
    private ExecutorService executorService;
    private UfoModel ufoModel;
    private List<ClientHandler> clients;
    private List<String> clientsUserNames;
    private ClientHandler firstClient;

    public Server(int port) throws IOException {
        this.port = port;
        this.executorService = Executors.newCachedThreadPool();
        this.clients = new CopyOnWriteArrayList<>();
        this.clientsUserNames = new CopyOnWriteArrayList<>();
        this.ufoModel = new UfoModel(this);
    }

    public void openConnection() {
        try {
            initializeServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializeServerSocket(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        String serverIp = getEthernetIp();
        System.out.println("Servidor iniciado en la IP " + serverIp + " y puerto " + port);
    }

      private String getEthernetIp() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (isValidNetworkInterface(networkInterface)) {
                String ip = getSiteLocalAddress(networkInterface);
                if (ip != null) {
                    return ip;
                }
            }
        }
        return null;
    }

    private boolean isValidNetworkInterface(NetworkInterface networkInterface) throws SocketException {
        return !networkInterface.isLoopback() && networkInterface.isUp();
    }

    private String getSiteLocalAddress(NetworkInterface networkInterface) {
        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress address = addresses.nextElement();
            if (address.isSiteLocalAddress()) {
                return address.getHostAddress();
            }
        }
        return null;
    }

    public void acceptClient() {
        while (true) {
            try {
                waitForClient();
            } catch (IOException e) {
                System.out.println("Error al aceptar cliente: " + e.getMessage());
            }
        }
    }

    private void waitForClient() throws IOException {
        System.out.println("Esperando cliente...");
        Socket clientSocket = serverSocket.accept();
        System.out.println("Cliente conectado");
        handleNewClient(clientSocket);
    }

    private void handleNewClient(Socket clientSocket) throws IOException {
        ClientHandler clientHandler = new ClientHandler(clientSocket, ufoModel, this);
        clients.add(clientHandler);
        assignFirstClient(clientHandler);
        executorService.submit(clientHandler);
    }

    private void assignFirstClient(ClientHandler clientHandler) {
        if (firstClient == null) {
            firstClient = clientHandler;
            firstClient.setFirst(true);
        } else {
            clientHandler.setFirst(false);
        }
    }

    public synchronized void sendMessageToAllClients(String message) throws IOException {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public synchronized void addClientUserName(String userName) {
        if (clientsUserNames.contains(userName)) {
            
        } else {
            clientsUserNames.add(userName);
            System.out.println("Usuario agregado: " + userName);
        }
    }

    public synchronized void removeClientUserName(String userName) {
        if (clientsUserNames.remove(userName)) {
            System.out.println("Usuario eliminado: " + userName);
        } else {
            System.out.println("El nombre de usuario no se encontró: " + userName);
        }
    }

    public synchronized List<String> getClientsUserNames() {
        return new ArrayList<>(clientsUserNames);
    }

    public synchronized void removeClient(ClientHandler clientHandler) {
        if (clients.remove(clientHandler)) {
            System.out.println("Cliente desconectado. Clientes restantes: " + clients.size());
        }
    }
}
