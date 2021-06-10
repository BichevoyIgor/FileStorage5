package server;

import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final int PORT = 9997;
    private ExecutorService service;

    public Server() {
        service = Executors.newFixedThreadPool(3);
        if (!SQLHandler.connect()) {
            throw new RuntimeException("Не удалось подключиться");
        }
        try (ServerSocket server = new ServerSocket(PORT)) {
            while (true) {
                service.execute(new ClientHandler(server.accept()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
