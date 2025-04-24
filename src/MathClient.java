import java.io.*;
import java.net.*;
import java.util.concurrent.TimeUnit;

public class MathClient {
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String clientName;

    public static void main(String[] args) {
        try {
            MathClient client = new MathClient();
            client.startConnection("localhost", 12345);  // Using the port from MathServer

            // Flag to track if the client should be running
            final boolean[] running = {true};

            // Start a new thread to read messages from the server
            Thread readThread = new Thread(() -> {
                try {
                    String message;
                    while ((message = client.receiveMessage()) != null) {
                        System.out.println(message);
                        System.out.print("\nEnter command (generate, leave, or expression): ");
                    }
                    // If we reach here, the server has closed the connection (readLine() returned null)
                    System.out.println("\n*** Server has closed the connection ***");
                    running[0] = false;
                    client.stopConnection();
                } catch (IOException e) {
                    System.err.println("\n*** Error on server side: " + e.getMessage() + " ***");
                    System.err.println("*** Closing client connection ***");
                    running[0] = false;
                    try {
                        client.stopConnection();
                    } catch (IOException ex) {
                        // Ignore, already trying to close
                    }
                }
            });

            // Start the read thread
            readThread.setDaemon(true); // This ensures the thread won't prevent the program from exiting
            readThread.start();

            // Start a new thread to read input from the console
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            
            System.out.println("Commands:");
            System.out.println("  'generate' - Generate a random expression");
            System.out.println("  'leave' - Disconnect from server");
            System.out.println("  Any other input will be sent as a calculation expression");
            
            String input;
            while (running[0] && (input = consoleReader.readLine()) != null) {
                if (input.equalsIgnoreCase("generate")) {
                    client.generateMathExpression();
                } else if (input.equalsIgnoreCase("leave")) {
                    client.sendMessage("LEAVE:" + client.clientName);
                    client.stopConnection();
                    break;
                } else if (!input.trim().isEmpty()) {
                    try {
                        client.sendMessage("CALC:" + client.clientName + ":" + input);
                    } catch (IOException e) {
                        System.err.println("\n*** Error communicating with server: " + e.getMessage() + " ***");
                        System.err.println("*** Closing client connection ***");
                        client.stopConnection();
                        break;
                    }
                }
                
                // Don't display prompt here, as the receive thread will do it
                
                // Check if we need to exit due to server disconnection
                if (!running[0]) {
                    break;
                }
            }
            
            System.out.println("Client terminated.");
            
        } catch (IOException e) {
            System.err.println("Failed to start client: " + e.getMessage());
        }
    }
    public void startConnection(String ip, int port) throws IOException {
        socket = new Socket(ip, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        
        // Get client name from user
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Enter your name: ");
        clientName = consoleReader.readLine();

        // Send JOIN message to server
        sendMessage("JOIN:" + clientName);
    }

    public void sendMessage(String message) throws IOException {
        out.write(message);
        out.newLine();
        out.flush();
        
        // Don't wait for response here - it's handled by the readThread
    }

    public String receiveMessage() throws IOException {
        return in.readLine();
    }


    public void stopConnection() throws IOException {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } finally {
            // Ensure these are null even if closing throws an exception
            in = null;
            out = null;
            socket = null;
        }
    }

    public void generateMathExpression() {
        StringBuilder expression = new StringBuilder();
        // Random object for generating random numbers and operators
        java.util.Random random = new java.util.Random();
        // Choose number of operands (3-5)
        int numCount = random.nextInt(3) + 3;  // Generates 3, 4, or 5
        
        // Available operators
        char[] operators = {'+', '-', '*', '/', '%'};
        
        // Add first number (1-100)
        expression.append(random.nextInt(100) + 1);
        
        // Add remaining numbers with operators
        for (int i = 1; i < numCount; i++) {
            // Add random operator
            char op = operators[random.nextInt(operators.length)];
            expression.append(op);
            
            // Add random number
            expression.append(random.nextInt(100) + 1);
        }
        
        // Return the expression as a string
        try {
            System.out.println("Generated expression: " + expression.toString());
            sendMessage("CALC:" + clientName + ":" + expression.toString());
        } catch (IOException e) {
            System.err.println("Error sending generated expression: " + e.getMessage());
        }
    }
    
}
