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
import java.util.Map;
import java.util.HashMap;


public class MathServer {
    private static ServerSocket serverSocket;
    private static final int PORT = 12345;
    private static final String LOG_FILE = "logs/server.log";
    private static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Thread pool for handling client connections
    private static ExecutorService clientPool = Executors.newCachedThreadPool();

    // Map to store client connections
    private static Map<String, ClientHandler> clients = new HashMap<>();

    public static void main(String[] args) {
        try {
            serverSocket = new ServerSocket(PORT);
            log("SERVER STARTED on port " + PORT);

            while (true) {
                // Accept client connection on a new thread
                final Socket clientSocket = serverSocket.accept();
                clientPool.execute(new ClientHandler(clientSocket));
                log("Client connected: " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
            }
        } catch (IOException e) {
            log("Error in server: " + e.getMessage());
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

    private static class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            final String clientAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            BufferedReader in = null;
            BufferedWriter out = null;
            String clientName = null;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                //Handle messages from the client   
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("client message: " + line);
                    String[] parts = line.split(":");
                    String command = parts[0];
                    
                    if (command.equals("JOIN")) {
                        // Add client to the list of clients
                        clientName = parts[1];
                        clients.put(clientName, this);
                        log("CONNECT - " + clientName + ": Connected from " + clientAddress);
                        
                        // Send ACK response
                        out.write("\nACK:" + clientName + ": Successfully joined the server");
                        out.newLine();
                        out.flush();
                    } else if (command.equals("CALC")) {
                        // Get client name and expression
                        clientName = parts[1];
                        String expression = parts[2];
                        
                        // Calculate the result of the expression
                        String result = calculate(expression);
                        out.write("\nRES:" + clientName + ":" + result);
                        
                        log("CALC_REQUEST - " + clientName + ": Expression received: " + expression);
                        log("CALC_RESPONSE - " + clientName + ": Result computed: " + result);
                        
                        out.newLine();
                        out.flush();
                    } else if (command.equals("LEAVE")) {
                        clientName = parts[1];
                        clients.remove(clientName);
                        break; // Exit the loop to close the connection
                    }
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

    private static String calculate(String expression) {
        try {
            // Parse the expression
            System.out.println("Expression: " + expression);
            String[] tokens = expression.split("(?<=[-+*/%])|(?=[-+*/%])");
            double result = Double.parseDouble(tokens[0]);
            
            // Process each token
            for (int i = 1; i < tokens.length; i += 2) {
                String operator = tokens[i];
                double number = Double.parseDouble(tokens[i + 1]);
                
                switch (operator) {
                    case "+":
                        result += number;
                        break;
                    case "-":
                        result -= number;
                        break;
                    case "*":
                        result *= number;
                        break;
                    case "/":
                        result /= number;
                        break;
                    case "%":
                        result %= number;
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid operator: " + operator);
                }
            }
            
            return String.valueOf(result);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid expression: " + e.getMessage());
        }
    }

    private static void log(final String message) {
        final String timestamped = "[" + TIMESTAMP.format(new Date()) + "] " + message;
        System.out.println(timestamped);
        
        java.io.File logDir = new java.io.File("logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            out.println(timestamped);
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }
}
