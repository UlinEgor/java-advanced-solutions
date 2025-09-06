package info.kgeorgiy.ja.ulin.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HelloUDPServer implements HelloServer {
    private DatagramSocket socket;
    private ExecutorService executor;
    private volatile boolean running = true;

    /**
     * UDP server, that receive request from client and sent answer.
     *
     * @param args format is "port threads".
     */
    public static void main(String[] args) {
        if (args == null || args.length != 2) {
            System.err.println("Incorrect number of argument, expected 2: \"port threads\"");
            return;
        }

        int port = Integer.parseInt(args[0]);
        int threads = Integer.parseInt(args[1]);

        Scanner scanner = new Scanner(System.in);

        try (HelloUDPServer server = new HelloUDPServer()) {
            server.start(port, threads);

            System.out.println("Enter 'q', to stop scanner");

            while (true) {
                String input = scanner.nextLine();
                if (input.equals("q")) {
                    break;
                }
            }
        }
    }

    @Override
    public void start(final int port, final int threads) {
        try {
            socket = new DatagramSocket(port);
            executor = Executors.newFixedThreadPool(threads);

            int bufferSize = socket.getReceiveBufferSize();

            for (int i = 0; i < threads; ++i) {

                executor.submit(() -> {

                    DatagramPacket packet = new DatagramPacket(new byte[bufferSize], bufferSize);

                    while (!socket.isClosed() && running) {
                        try {
                            socket.receive(packet);

                            byte[] requestData = new byte[packet.getLength()];
                            System.arraycopy(packet.getData(), packet.getOffset(), requestData, 0, packet.getLength());;

                            handle(new String(requestData, StandardCharsets.UTF_8), packet.getSocketAddress());
                        } catch (IOException e) {
                            System.err.println("Error: " + e.getMessage());
                        }
                    }
                });
            }
        } catch (SocketException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private void handle(final String received, final SocketAddress address) {
        String responseText = "Hello, " + received;
        byte[] responseData = responseText.getBytes(StandardCharsets.UTF_8);
        DatagramPacket response = new DatagramPacket(responseData, responseData.length, address);

        try {
            socket.send(response);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (executor != null && !executor.isShutdown()) {
            executor.close();
        }
    }
}
