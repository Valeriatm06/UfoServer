package co.edu.uptc.models;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import co.edu.uptc.utilities.ClientHandler;
import co.edu.uptc.models.UfoModel;  // Importamos UfoModel

public class Server {

    private ServerSocket serverSocket;
    private int port;
    private ExecutorService executorService;
    private UfoModel ufoModel;  // Creamos una instancia de UfoModel

    public Server(int port) throws IOException {
        this.port = port;
        executorService = Executors.newCachedThreadPool();
          // Inicializamos el modelo de UFOs
    }

    public void openConnection() {
        try {
            serverSocket = new ServerSocket(port);  // Creamos el ServerSocket
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
                ufoModel = new UfoModel(clientSocket); // Acepta la conexión de un cliente
                System.out.println("Cliente conectado");

                // Ahora pasamos la instancia de UfoModel al ClientHandler
                executorService.submit(new ClientHandler(clientSocket, ufoModel));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
