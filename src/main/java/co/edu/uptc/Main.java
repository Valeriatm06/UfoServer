package co.edu.uptc;

import java.io.IOException;

import co.edu.uptc.models.Server;

public class Main {
    public static void main(String[] args) throws IOException {
        Server server = new Server(3099);
        server.openConnection();
        server.acceptClient();
    }
}