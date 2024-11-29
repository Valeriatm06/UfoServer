package co.edu.uptc.utilities;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import com.google.gson.Gson;  
import com.google.gson.GsonBuilder;
import java.awt.Point;

import co.edu.uptc.models.Server;
import co.edu.uptc.models.UfoModel;
import co.edu.uptc.pojos.Ufo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private DataInputStream input;
    private DataOutputStream output;
    private int ufoCount;
    private int speed;
    private int appearanceTime;
    private int delta;
    private int newXPosition;
    private int newYPosition;
    private Ufo selectedUfo;
    private UfoModel ufoModel;
    private boolean isFirst;
    private Server server;

    public ClientHandler(Socket clientSocket, UfoModel ufoModel, Server server) throws IOException {
        this.clientSocket = clientSocket;
        this.input = new DataInputStream(clientSocket.getInputStream());
        this.output = new DataOutputStream(clientSocket.getOutputStream());
        this.ufoModel = ufoModel;
        this.isFirst = false;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = receiveMessage()) != null) {
                // System.out.println("Mensaje recibido: " + message);
                if (message.startsWith("UFO_COUNT")) {
                    ufoCount = Integer.parseInt(message.split(" ")[1]);
                    System.out.println("Número de UFOs: " + ufoCount);
                } else if (message.startsWith("SPEED")) {
                    speed = Integer.parseInt(message.split(" ")[1]);
                    System.out.println("Velocidad: " + speed);
                } else if (message.startsWith("APPEARANCE_TIME")) {
                    appearanceTime = Integer.parseInt(message.split(" ")[1]);
                    System.out.println("Tiempo de aparición: " + appearanceTime);
                } else if (message.startsWith("SELECTED_UFO_SPEED")) {
                    delta = Integer.parseInt(message.split(" ")[1]);
                    System.out.println("Velocidad nueva: " + delta);
                } else if (message.startsWith("X_POSITION")) {
                    newXPosition = Integer.parseInt(message.split(" ")[1]);
                    System.out.println("Posición en x: " + newXPosition);
                } else if (message.startsWith("Y_POSITION")) {
                    newYPosition = Integer.parseInt(message.split(" ")[1]);
                    System.out.println("Posición en y: " + newYPosition);
                } else if (message.startsWith("SELECTED_UFO")) {
                    handleSelectedUfo(message);
                } else if (message.startsWith("SEND_UFOS")) {
                    sendUfosList();
                } else if (message.startsWith("SEND_RUNNING_STATE")) {
                    sendRunningStatus();
                }else if (message.startsWith("IS_FIRST")) {
                    sendFirstClient();
                }else if (message.startsWith("SEND_UFOS_STOPPED")) {
                    sendAllUfosStopped();
                    System.out.println(ufoModel.allUfosStopped());
                }else if (message.startsWith("START_GAME")) {
                    ufoModel.startGame(ufoCount, speed, appearanceTime);
                }else if (message.startsWith("SEND_SELECTED_UFO")) {
                    sendSelectedUfoAtPosition();
                }else if (message.startsWith("CHANGE_SELECTED_UFO_SPEED")) {
                    ufoModel.changeSelectedUfoSpeed(delta);
                    server.sendMessageToAllClients("Cambio de velocidad " + delta);
                }else if (message.startsWith("START_UFO_MOVEMENT")) {
                    ufoModel.startUfoMovement(selectedUfo);
                }else {
                    System.out.println("Comando desconocido: " + message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();  
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String receiveMessage() throws IOException {
        return input.readUTF();  
    }

    public synchronized void sendMessage(String msg) throws IOException {
        output.writeUTF(msg); 
        output.flush();  
    }

   
    private void sendUfosList() throws IOException {
        Gson gson = new Gson();
        String ufosJson = gson.toJson(ufoModel.getUfosList()); 

        server.sendMessageToAllClients("UFOS_LIST " + ufosJson);
    }

    private void sendSelectedUfoAtPosition() {
        Ufo ufo = ufoModel.selectUfoAtPosition(newXPosition, newYPosition);
    
        if (ufo != null) {
            Gson gson = new Gson();
            String ufoJson = gson.toJson(ufo);
    
            try {
                sendMessage("SINGLE_UFO " + ufoJson);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                server.sendMessageToAllClients("ERROR UFO not found at position (" + newXPosition + ", " + newYPosition + ")");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleSelectedUfo(String message) {
        String selectedUfoJson = message.substring("SELECTED_UFO".length()).trim();
        try {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(Point.class, new PointAdapter()); 
            Gson gson = gsonBuilder.create();
    
            selectedUfo = gson.fromJson(selectedUfoJson, Ufo.class);
    
            System.out.println("UFO recibido: " + selectedUfo);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error al deserializar el JSON: " + selectedUfoJson);
        }
    }
    

    private void sendAllUfosStopped() throws IOException{
        boolean isStopped = ufoModel.allUfosStopped();
        server.sendMessageToAllClients("UFO_STOPPED " + isStopped);
    }

    private void sendRunningStatus() throws IOException {
        boolean isRunning = ufoModel.isRunning(); 
        server.sendMessageToAllClients("UFO_RUNNING " + isRunning);  
    }

    private void sendFirstClient() throws IOException{
        server.sendMessageToAllClients("FIRST_CLIENT " + isFirst);
    }
}
