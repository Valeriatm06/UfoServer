package co.edu.uptc.utilities;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.Gson;  
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

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
    private List<Point> selectedUfoTrayectory;
    private UfoModel ufoModel;
    private boolean isFirst;
    private Server server;
    private String userName;

    public ClientHandler(Socket clientSocket, UfoModel ufoModel, Server server) throws IOException {
        this.clientSocket = clientSocket;
        this.input = new DataInputStream(clientSocket.getInputStream());
        this.output = new DataOutputStream(clientSocket.getOutputStream());
        this.ufoModel = ufoModel;
        this.isFirst = false;
        this.server = server;
        selectedUfoTrayectory =  new CopyOnWriteArrayList<>();
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
                } else if (message.startsWith("USER_NAME")) {
                    userName = message.split(" ")[1];
                    server.addClientUserName(userName);
                    sendUserList();
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
                } else if (message.startsWith("SEND_USER_LIST")) {
                    sendUserList();
                }else if (message.startsWith("SEND_RUNNING_STATE")) {
                    sendRunningStatus();
                }else if (message.startsWith("IS_FIRST")) {
                    sendFirstClient();
                    System.out.println(isFirst);
                }else if (message.startsWith("SEND_UFOS_STOPPED")) {
                    sendAllUfosStopped();
                    
                }else if (message.startsWith("START_GAME")) {
                    ufoModel.startGame(ufoCount, speed, appearanceTime);
                }else if (message.startsWith("SEND_SELECTED_UFO")) {
                    sendSelectedUfoAtPosition();
                }else if (message.startsWith("CHANGE_SELECTED_UFO_SPEED")) {
                    ufoModel.changeSelectedUfoSpeed(delta);
                    server.sendMessageToAllClients("Cambio de velocidad " + delta);
                }else if (message.startsWith("START_UFO_MOVEMENT")) {
                    ufoModel.startUfoMovement(selectedUfo);
                }else if (message.startsWith("TRAYECTORY")) {
                    handleUfoTrayectory(message);
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
                System.out.println("Se cerro la conexión con el cliente");;
            }
            server.removeClientUserName(userName);
            server.removeClient(this);  
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

    private void sendUserList(){
        List<String> stringList = server.getClientsUserNames();
        Gson gson = new Gson();
        String jsonList = gson.toJson(stringList);
        System.out.println("usuarios " + jsonList);
        try {
            sendMessage("USERS_LIST " + jsonList);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error al enviar la lista de strings al cliente.");
        }
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
    

    private void handleUfoTrayectory(String message) {
        String trayectoryJson = message.substring("TRAYECTORY".length()).trim();
        try {
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(Point.class, new PointAdapter());  
            Gson gson = gsonBuilder.create();
            
            Type listType = new TypeToken<List<Point>>() {}.getType();  
            selectedUfoTrayectory = gson.fromJson(trayectoryJson, listType);
            if (selectedUfo != null) {
                selectedUfo.setTrajectory(selectedUfoTrayectory);  
            } else {
                System.out.println("Trayectoria recibida pero no hay UFO seleccionado.");
            }
            add();
        } catch (JsonParseException e) {
            e.printStackTrace();
            System.out.println("Error al deserializar la trayectoria del UFO: " + trayectoryJson);
        }
    }

    private void sendAllUfosStopped() throws IOException{
        boolean isStopped = ufoModel.allUfosStopped();
        server.sendMessageToAllClients("UFO_STOPPED " + String.valueOf(isStopped));
    }

    private void sendRunningStatus() throws IOException {
        boolean isRunning = ufoModel.isRunning(); 
        server.sendMessageToAllClients("UFO_RUNNING " + String.valueOf(isRunning));  
    }

    private void sendFirstClient() throws IOException{
        server.sendMessageToAllClients("FIRST_CLIENT " + String.valueOf(isFirst));
    }

    private synchronized void add() {
        ufoModel.addTrayectory(selectedUfoTrayectory);
    }
    
}
