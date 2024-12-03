package co.edu.uptc.models;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
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
        executorService = Executors.newCachedThreadPool();
        this.clients = new CopyOnWriteArrayList<>(); 
        this.clientsUserNames = new CopyOnWriteArrayList<>();
        ufoModel = new UfoModel(this);
    }

    public void openConnection() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Puerto disponible " + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void acceptClient() {
        while (true) {
            try {
                System.out.println("Esperando cliente...");
                Socket clientSocket = serverSocket.accept();
                System.out.println("Cliente conectado");
                ClientHandler clientHandler = new ClientHandler(clientSocket, ufoModel, this);
                clients.add(clientHandler);  
                if (firstClient == null) {
                    firstClient = clientHandler;
                    firstClient.setFirst(true);
                }else{
                    clientHandler.setFirst(false);
                }
                executorService.submit(clientHandler);
            } catch (IOException e) {
                System.out.println("Error al aceptar cliente: " + e.getMessage());
            }
        }
    }

    public synchronized void sendMessageToAllClients(String message) throws IOException{
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public synchronized void addClientUserName(String userName) {
        if (!clientsUserNames.contains(userName)) {
            clientsUserNames.add(userName);
            System.out.println("Usuario agregado: " + userName);
        } else {
            System.out.println("El nombre de usuario ya existe: " + userName);
        }
    }

    public synchronized void removeClientUserName(String userName) {
        if (clientsUserNames.contains(userName)) {
            clientsUserNames.remove(userName);
            System.out.println("Usuario eliminado: " + userName);
        } else {
            System.out.println("El nombre de usuario no se encontró: " + userName);
        }
    }

    public synchronized List<String> getClientsUserNames() {
        return new ArrayList<>(clientsUserNames); 
    }


    public synchronized void removeClient(ClientHandler clientHandler) {
        if (clients.contains(clientHandler)) {
            clients.remove(clientHandler);
            System.out.println("Cliente desconectado. Clientes restantes: " + clients.size());
        }
    }
    
}
