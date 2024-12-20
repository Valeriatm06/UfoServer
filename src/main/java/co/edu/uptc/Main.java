package co.edu.uptc;

import java.io.IOException;

import co.edu.uptc.models.Server;

public class Main {
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        Server server = new Server(port);
        server.openConnection();
        server.acceptClient();
    }
}