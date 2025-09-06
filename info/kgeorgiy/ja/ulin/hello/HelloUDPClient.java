package info.kgeorgiy.ja.ulin.hello;

import info.kgeorgiy.java.advanced.hello.NewHelloClient;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class HelloUDPClient implements NewHelloClient {
    private static final int TIMEOUT = 200;

    /**
     * UDP client, that send request on server and check response corrects.
     * Send message with template `prefix + "_" + thread`.
     *
     * @param args format is "host port prefix threads requests".
     */
    public static void main(String[] args) {
        if (args == null || args.length != 5) {
            System.err.println("Incorrect number of argument, expected 5: \"host port prefix threads requests\"");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String prefix = args[2];
        int threads = Integer.parseInt(args[3]);
        int requests = Integer.parseInt(args[4]);

        HelloUDPClient client = new HelloUDPClient();
        client.run(host, port, prefix, threads, requests);
    }

    @Override
    public void newRun(final List<Request> requests, final int threads) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int thread = 0; thread < threads; thread++) {
            final int finalThread = thread;

            executor.submit(() -> {
                try (DatagramSocket socket = new DatagramSocket()) {
                    int bufferSize = socket.getReceiveBufferSize();
                    socket.setSoTimeout(TIMEOUT);
                    byte[] buffer = new byte[bufferSize];
                    DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);

                    for (Request request : requests) {
                        String message = request.template().replaceAll("\\$", String.valueOf(finalThread + 1));

                        byte[] requestData = message.getBytes(StandardCharsets.UTF_8);

                        DatagramPacket requestPacket = new DatagramPacket(
                                requestData, requestData.length,
                                InetAddress.getByName(request.host()), request.port()
                        );

                        sendPacket(socket, requestPacket, responsePacket, message);
                    }
                } catch (IOException e) {
                    System.err.println("Error: " + e.getMessage());
                }
            });
        }

        executor.close();
    }

    private static void sendPacket(final DatagramSocket socket, final DatagramPacket requestPacket, final DatagramPacket responsePacket, final String message) {
        while (true) {
            try {
                socket.send(requestPacket);
                socket.receive(responsePacket);

                String response = new String(
                        responsePacket.getData(),
                        responsePacket.getOffset(),
                        responsePacket.getLength(),
                        StandardCharsets.UTF_8
                );

                if (Objects.equals(getNumbers(response), getNumbers(message))) {
                    System.out.println("Sent: " + message + ", receive: " + response);
                    break;
                } else {
                    System.out.println("Sent message: " + message + ", but get incorrect response: " + response);
                }
            } catch (SocketTimeoutException ignore) {
                System.out.println("Timeout receive message");
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
                break;
            }
        }
    }

    private static List<Integer> getNumbers(final String s) {
        return s.chars()
                .filter(Character::isDigit)
                .mapToObj(c -> Integer.parseInt(String.valueOf((char) c)))
                .collect(Collectors.toList());
    }
}
