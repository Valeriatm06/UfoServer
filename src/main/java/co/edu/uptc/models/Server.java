package co.edu.uptc.models;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import co.edu.uptc.utilities.ClientHandler;

public class Server {

    private ServerSocket serverSocket;
    private int port;
    private ExecutorService executorService;
    private UfoModel ufoModel;
    private List<ClientHandler> clients;
    private ClientHandler firstClient;

    public Server(int port) throws IOException {
        this.port = port;
        executorService = Executors.newCachedThreadPool();
        this.clients = new ArrayList<>(); 
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
                if(firstClient == null){
                    firstClient = clientHandler;
                    firstClient.setFirst(true);
                }
                executorService.submit(clientHandler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void sendMessageToAllClients(String message) throws IOException{
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }
}
