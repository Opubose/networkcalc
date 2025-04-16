import java.net.Socket;
import java.net.ServerSocket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MathServer {
    private static ServerSocket serverSocket;
    private static final int PORT = 12345;
    private static final String LOG_FILE = "logs/server.log";
    private static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static ExecutorService clientPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        try {
            serverSocket = new ServerSocket(PORT);
            log("SERVER STARTED on port " + PORT);

            while (true) {
                final Socket clienSocket = serverSocket.accept();
                clientPool.execute(new ClientHandler(clienSocket));
            }
        } catch (IOException e) {
            System.err.println("Error in server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing server socket " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private static void log(final String message) {
        final String timestamped = "[" + TIMESTAMP.format(new Date()) + "] " + message;
        System.out.println(timestamped);
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            out.println(timestamped);
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private String clientName = "Unknown";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            // TODO: get client name from the client socket and store it in clientName
            final String clientAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            
            try { 
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                log("CONNECT - " + clientName + ": Connected from " + clientAddress);

                String line;
                while ((line = in.readLine()) != null) {
                    // TODO: Parse and handle JOIN, CALC, QUIT messages
                    // Respond to client using out.write(...); out.newLine(); out.flush();
                }

            } catch (IOException e) {
                log("ERROR with client - " + clientName + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {}
                log("DISCONNECT - " + clientName + " disconnected");
            }
        }
    }
}
