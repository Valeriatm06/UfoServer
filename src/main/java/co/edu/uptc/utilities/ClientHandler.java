package co.edu.uptc.utilities;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import co.edu.uptc.models.Server;
import co.edu.uptc.models.UfoModel;
import co.edu.uptc.pojos.Ufo;

import java.awt.Point;
import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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
    private int ufoCount, speed, appearanceTime, delta, newXPosition, newYPosition;
    private Ufo selectedUfo;
    private List<Point> selectedUfoTrayectory;
    private UfoModel ufoModel;
    private boolean isFirst;
    private Server server;
    private String userName;

    private Map<String, Consumer<String>> commandHandlers;

    public ClientHandler(Socket clientSocket, UfoModel ufoModel, Server server) throws IOException {
        this.clientSocket = clientSocket;
        this.input = new DataInputStream(clientSocket.getInputStream());
        this.output = new DataOutputStream(clientSocket.getOutputStream());
        this.ufoModel = ufoModel;
        this.server = server;
        this.isFirst = false;
        this.selectedUfoTrayectory = new CopyOnWriteArrayList<>();
        this.commandHandlers = new HashMap<>();
        initializeCommandHandlers();
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = receiveMessage()) != null) {
                String command = extractCommand(message);
                commandHandlers.getOrDefault(command, this::unknownCommand).accept(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            cleanUp();
        }
    }

    private void initializeCommandHandlers() {
        initializeGameControlCommands();
        initializeUfoControlCommands();
        initializeClientCommunicationCommands();
    }
    

    private void initializeGameControlCommands() {
        commandHandlers.put("START_GAME", msg -> ufoModel.startGame(ufoCount, speed, appearanceTime));
        commandHandlers.put("SEND_RUNNING_STATE", msg -> {sendRunningStatus();});
        commandHandlers.put("IS_FIRST", msg -> {sendFirstClient();});
    }

    private void initializeUfoControlCommands() {
        commandHandlers.put("UFO_COUNT", this::handleUfoCount);
        commandHandlers.put("SPEED", msg -> speed = parseIntParameter(msg));
        commandHandlers.put("X_POSITION", msg -> newXPosition = parseIntParameter(msg));
        commandHandlers.put("Y_POSITION", msg -> newYPosition = parseIntParameter(msg));
        commandHandlers.put("APPEARANCE_TIME", msg -> appearanceTime = parseIntParameter(msg));
        commandHandlers.put("SELECTED_UFO_SPEED", msg -> delta = parseIntParameter(msg));
        commandHandlers.put("START_UFO_MOVEMENT", msg -> ufoModel.startUfoMovement(selectedUfo));
        commandHandlers.put("CHANGE_SELECTED_UFO_SPEED", msg -> {changeSelectedUfoSpeed();});
        commandHandlers.put("TRAYECTORY", this::handleUfoTrayectory);
        commandHandlers.put("SEND_SELECTED_UFO", msg -> sendSelectedUfoAtPosition());
        commandHandlers.put("SELECTED_UFO", this::handleSelectedUfo);
    }
    
    private void initializeClientCommunicationCommands() {
        commandHandlers.put("USER_NAME", this::handleUserName);
        commandHandlers.put("SEND_UFOS", msg -> sendUfosList());
        commandHandlers.put("SEND_USER_LIST", msg -> sendUserList());
        commandHandlers.put("SEND_UFOS_STOPPED", msg -> {sendAllUfosStopped();});
    }

    private String receiveMessage() throws IOException {
        return input.readUTF();
    }

    public synchronized void sendMessage(String msg) throws IOException {
        output.writeUTF(msg);
        output.flush();
    }

    private void cleanUp() {
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.out.println("Conexión con cliente cerrada.");
        }
        server.removeClientUserName(userName);
        server.removeClient(this);
    }

    private String extractCommand(String message) {
        return message.contains(" ") ? message.split(" ")[0] : message;
    }

    private int parseIntParameter(String message) {
        return Integer.parseInt(message.split(" ")[1]);
    }

    private void unknownCommand(String message) {
        System.out.println("Comando desconocido: " + message);
    }

    private void handleUfoCount(String message) {
        ufoCount = parseIntParameter(message);
        System.out.println("Número de UFOs: " + ufoCount);
    }

    private void handleUserName(String message) {
        userName = message.split(" ")[1];
        server.addClientUserName(userName);
        sendUserList();
    }

    private void handleSelectedUfo(String message) {
        String selectedUfoJson = message.substring("SELECTED_UFO".length()).trim();
        try {
            Gson gson = createGsonWithPointAdapter();
            selectedUfo = gson.fromJson(selectedUfoJson, Ufo.class);
            System.out.println("UFO recibido: " + selectedUfo);
        } catch (JsonParseException e) {
            e.printStackTrace();
            System.out.println("Error al deserializar el JSON: " + selectedUfoJson);
        }
    }

    private void handleUfoTrayectory(String message) {
        String trayectoryJson = message.substring("TRAYECTORY".length()).trim();
        try {
            Gson gson = createGsonWithPointAdapter();
            Type listType = new TypeToken<List<Point>>() {}.getType();
            selectedUfoTrayectory = gson.fromJson(trayectoryJson, listType);
            // if (selectedUfo != null) {
            //     selectedUfo.setTrajectory(selectedUfoTrayectory);
            // } else {
            //     System.out.println("Trayectoria recibida pero no hay UFO seleccionado.");
            // }
            ufoModel.addTrayectory(selectedUfoTrayectory);
        } catch (JsonParseException e) {
            e.printStackTrace();
        }
    }

    private Gson createGsonWithPointAdapter() {
        return new GsonBuilder().registerTypeAdapter(Point.class, new PointAdapter()).create();
    }

    private void sendUfosList() {
        try {
            String ufosJson = new Gson().toJson(ufoModel.getUfosList());
            server.sendMessageToAllClients("UFOS_LIST " + ufosJson);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendUserList() {
        try {
            String jsonList = new Gson().toJson(server.getClientsUserNames());
            sendMessage("USERS_LIST " + jsonList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendSelectedUfoAtPosition() {
        Ufo ufo = ufoModel.selectUfoAtPosition(newXPosition, newYPosition);
        try {
            if (ufo != null) {
                String ufoJson = new Gson().toJson(ufo);
                sendMessage("SINGLE_UFO " + ufoJson);
            } else {
                server.sendMessageToAllClients("ERROR UFO not found at position (" + newXPosition + ", " + newYPosition + ")");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendAllUfosStopped(){
        boolean isStopped = ufoModel.allUfosStopped();
        try {
            server.sendMessageToAllClients("UFO_STOPPED " + isStopped);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendRunningStatus(){
        boolean isRunning = ufoModel.isRunning();
        try {
            server.sendMessageToAllClients("UFO_RUNNING " + isRunning);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendFirstClient(){
        try {
            server.sendMessageToAllClients("FIRST_CLIENT " + isFirst);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void changeSelectedUfoSpeed(){
        try {
            ufoModel.changeSelectedUfoSpeed(delta);
            server.sendMessageToAllClients("Cambio de velocidad " + delta);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
