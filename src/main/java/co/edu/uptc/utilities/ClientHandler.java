package co.edu.uptc.utilities;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import com.google.gson.Gson;  // Importamos la librería Gson
import co.edu.uptc.models.UfoModel;
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

    // Variables para almacenar los valores recibidos
    private int ufoCount;
    private int speed;
    private int appearanceTime;
    private int delta;

    // Clase que maneja la lógica del juego
    private UfoModel ufoModel;

    public ClientHandler(Socket clientSocket, UfoModel ufoModel) throws IOException {
        this.clientSocket = clientSocket;
        this.input = new DataInputStream(clientSocket.getInputStream());
        this.output = new DataOutputStream(clientSocket.getOutputStream());
        this.ufoModel = ufoModel;
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = receiveMessage()) != null) {
                System.out.println("Mensaje recibido: " + message);

                // Procesar el mensaje recibido
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
                    System.out.println("Tiempo de aparición: " + delta);
                } else if (message.startsWith("SEND_UFOS")) {
                    // Comando para enviar la lista de UFOs
                    sendUfosList();
                } else if (message.startsWith("SEND_RUNNING_STATE")) {
                    // Comando para enviar la lista de UFOs
                    sendRunningStatus();
                }else if (message.startsWith("START_GAME")) {
                    // Comando para enviar la lista de UFOs
                    ufoModel.startGame(ufoCount, speed, appearanceTime);
                    sendMessage("Juego iniciado con " + ufoCount + " UFOs, velocidad " + speed + " y tiempo de aparición " + appearanceTime + " ms.");
                }else if (message.startsWith("CHANGE_SELECTED_UFO_SPEED")) {
                    // Comando para enviar la lista de UFOs
                    ufoModel.changeSelectedUfoSpeed(delta);;
                    sendMessage("Cambio de velocidad " + delta);
                }else {
                    System.out.println("Comando desconocido: " + message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();  // Cerramos la conexión con el cliente
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Método para recibir un mensaje del cliente
    private String receiveMessage() throws IOException {
        return input.readUTF();  // Leer mensaje como UTF-8
    }

    // Método para enviar un mensaje al cliente
    public void sendMessage(String msg) throws IOException {
        output.writeUTF(msg);  // Enviar mensaje como UTF-8
        output.flush();  // Asegurarse de que el mensaje se envíe
    }

    // Método para enviar la lista de UFOs al cliente
    private void sendUfosList() throws IOException {
        // Convertir la lista de UFOs a JSON usando Gson
        Gson gson = new Gson();
        String ufosJson = gson.toJson(ufoModel.getUfosList());  // Convertir lista de UFOs a JSON

        // Enviar el JSON al cliente
        sendMessage("UFOS_LIST " + ufosJson);
    }

    private void sendRunningStatus() throws IOException {
        boolean isRunning = ufoModel.isRunning();  // Obtener el estado de isRunning desde UfoModel
        sendMessage("UFO_RUNNING " + isRunning);   // Enviar el estado de isRunning al cliente
    }
}
