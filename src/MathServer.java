import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class MathServer {
    private static final int PORT = 12345;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Thread pool configuration
    private static final int MAX_CONCURRENT_CLIENTS = 5; // Maximum number of concurrent client connections
    
    private static final ConcurrentMap<String, ClientHandler> clients = new ConcurrentHashMap<>();  // Stores the assigned ClientHandler object for each clientName
    private static final ConcurrentMap<String, Instant> connectTimes = new ConcurrentHashMap<>();   // Stores the time at which each client joined the server, indexed by clientName
    private static final BlockingQueue<CalcRequest> requestQueue = new LinkedBlockingQueue<>(); // A central queue to store all incoming calculation requests in FIFO order
    private static final ExecutorService clientPool = Executors.newFixedThreadPool(MAX_CONCURRENT_CLIENTS);

    private static final Map<String, Integer> prec = Map.of(
        "+", 1,
        "-", 1,
        "*", 2,
        "/", 2,
        "%", 2
    );

    /**
     * Main entry point for the server. Sets up logging, starts the request processor,
     * initializes console input handling for graceful shutdown, and begins accepting
     * client connections.
     * 
     * @param args Command-line arguments (not used)
     */
    public static void main(String[] args) {
        setupLogFile();
        startRequestProcessor();

        // Add shutdown hook for console input
        Thread consoleInput = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if ("quit".equalsIgnoreCase(line.trim())) {
                        log("CONNECT", "SERVER", "Server shutting down...");
                        System.exit(0);
                    }
                }
            } catch (IOException e) {
                log("ERR", "SERVER", "Error reading console input: " + e.getMessage());
            }
        });
        consoleInput.setDaemon(true);
        consoleInput.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log("CONNECT", "SERVER", "Server started on port " + PORT);
            while (true) {
                final Socket clientSocket = serverSocket.accept();
                clientPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            log("ERR", "SERVER", "Failed to start server: " + e.getMessage());
        } finally {
            clientPool.shutdown();
        }
    }

    /**
     * Launches a dedicated central background thread that continuously reads calculation requests in FIFO order.
     * This thread calls the relevant functions to calculate the output of the expressions and then creates a response to send back to the appropriate client based on that.
     * It also logs the incoming request and the outgoing response.
     */
    private static void startRequestProcessor() {
        final Thread processor = new Thread(() -> {
            while (true) {
                try {
                    final CalcRequest req = requestQueue.take();    // Take a calculation request from the FIFO queue
                    log("CALC_REQUEST", req.clientName, "Expression received: " + req.expression);
                    try {
                        final double value = calculate(req.expression);
                        final String result = formatResult(value);
                        req.handler.sendMessage("RES:" + req.clientName + ":" + result);
                        log("CALC_RESPONSE", req.clientName, req.expression + " = " + result);
                    } catch (IllegalArgumentException ex) {
                        req.handler.sendMessage("ERR:" + ex.getMessage());
                        log("ERR", req.clientName, ex.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "RequestProcessor");
        processor.setDaemon(true);
        processor.start();
    }

    /**
     * Calculates the arithmetical value of the provided expression using the standard Shunting Yard Algorithm
     * @param expression The arithmetic expression provided as a String
     * @return the computed value of the calculation as a String
     * @throws IllegalArgumentException if the expression is malformed
     */
    private static double calculate(final String expression) {
        List<String> tokens = tokenize(expression);
        List<String> output = new ArrayList<>();
        Stack<String> ops = new Stack<>();

        for (String token : tokens) {
            if (token.matches("\\d+(\\.\\d+)?")) {
                output.add(token);
            } else if (prec.containsKey(token)) {
                while (!ops.isEmpty()
                       && prec.containsKey(ops.peek())
                       && prec.get(ops.peek()) >= prec.get(token)) {
                    output.add(ops.pop());
                }
                ops.push(token);
            } else if ("(".equals(token)) {
                ops.push(token);
            } else if (")".equals(token)) {
                while (!ops.isEmpty() && !"(".equals(ops.peek())) {
                    output.add(ops.pop());
                }
                if (ops.isEmpty() || ! "(".equals(ops.pop())) {
                    throw new IllegalArgumentException("Invalid Expression Format");
                }
            } else {
                throw new IllegalArgumentException("Invalid Expression Format");
            }
        }
        while (!ops.isEmpty()) {
            final String op = ops.pop();
            if ("(".equals(op) || ")".equals(op)) {
                throw new IllegalArgumentException("Invalid Expression Format");
            }
            output.add(op);
        }
        
        Stack<Double> eval = new Stack<>();
        for (final String token : output) {
            if (prec.containsKey(token)) {
                if (eval.size() < 2) {
                    throw new IllegalArgumentException("Invalid Expression Format");
                }

                final double b = eval.pop();
                final double a = eval.pop();
                switch (token) {
                    case "+" -> eval.push(a + b);
                    case "-" -> eval.push(a - b);
                    case "*" -> eval.push(a * b);
                    case "/" -> eval.push(a / b);
                    case "%" -> eval.push(a % b);
                }
            } else {
                eval.push(Double.parseDouble(token));
            }
        }

        if (eval.size() != 1) {
            throw new IllegalArgumentException("Invalid Expression Format");
        }
        return eval.pop();
    }

    /**
     * Splits the given expression string into a {@code List} of numbers, operators, and parentheses
     * 
     * @param expr The string expression to tokenize
     * @return A list of tokens (numbers, operators, parentheses)
     */
    private static List<String> tokenize(final String expr) {
        final String spaced = expr.replaceAll("([()+\\-*/%])", " $1 ");
        final String[] parts = spaced.trim().split("\\s+");
        return List.of(parts);
    }

    /**
     * Utility function for converting the given double expression into a Long
     * @param result The calculated result as a double
     * @return A string representation of the result, as an integer if possible
     */
    private static String formatResult(final double result) {
        if (result == (long) result) {
            return Long.toString((long) result);
        }
        return Double.toString(result);
    }

    /**
     * First, this creates the log sub-directory if it doesn't exist already. Then, it makes a new server.log file, replacing a previous one, if it exists.
     * @throws IOException if the log dir could not be created
     */
    private static void setupLogFile() {
        try {
            final Path logDir = Paths.get("logs");
            Files.createDirectories(logDir);
            
            final Path logFile = logDir.resolve("server.log");
            if (Files.exists(logFile)) {
                Files.delete(logFile);
            }

            Files.createFile(logFile);
        } catch (IOException e) {
            System.err.println("Could not create log directory or file: " + e.getMessage());
        }
    }

    /**
     * Utility function for logging server events. All logs are printed to stdout and also to the log file in the logs sub-directory.
     * @param event The event being logged. They can be one of {@code JOIN},{@code CALC}, or {@code LEAVE}.
     * @param clientName The name of the client to which {@code event} belongs.
     * @param details Information about the event.
     */
    private static void log(final String event, final String clientName, final String details) {
        final String timestamp = LocalDateTime.now().format(TIMESTAMP);
        final String entry = String.format("[%s] %s - %s: %s", timestamp, event, clientName, details);
        System.out.println(entry);
        try {
            Files.writeString(
                Path.of("logs/server.log"),
                entry + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.err.println("Failed to write log: " + e.getMessage());
        }
    }

    
    /**
     * A basic utility class that tracks relevant information for each incoming calculation request
     */
    private static class CalcRequest {
        final String clientName;
        final String expression;
        final ClientHandler handler;

        /**
         * Creates a new calculation request with the specified parameters
         * 
         * @param clientName The name of the client making the request
         * @param expression The mathematical expression to calculate
         * @param handler The client handler instance to send the result back to
         */
        CalcRequest(final String clientName, final String expression, final ClientHandler handler) {
            this.clientName = clientName;
            this.expression = expression;
            this.handler = handler;
        }
    }

    /**
     * Handles communication with an individual client. Each client connection
     * is managed by its own instance of this class running in a separate thread.
     */
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private String clientName;
        private PrintWriter out;

        /**
         * Creates a new client handler for the specified socket
         * 
         * @param socket The client socket connection
         */
        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Main processing loop for client communication. Reads incoming messages,
         * parses them, and handles them according to the protocol.
         */
        @Override
        public void run() {
            final String clientAddr = socket.getRemoteSocketAddress().toString();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                this.out = writer;
                
                String line;
                while ((line = in.readLine()) != null) {
                    final String[] parts = line.split(":", 2);    // Parse the input from client

                    if (parts.length != 2) {
                        sendMessage("ERR:Invalid Expression Format");
                        log("ERR", "UNKNOWN", "Malformed command");
                        continue;
                    }

                    final String cmd = parts[0];
                    switch (cmd) {  // Process the request from client
                        case "JOIN" -> handleJoin(parts[1], clientAddr);
                        case "CALC" -> handleCalc(parts[1]);
                        case "LEAVE" -> {
                            sendMessage("ACK:" + clientName + ":Goodbye");
                            return;
                        }
                        default -> {
                            sendMessage("ERR:Invalid Expression Format");
                            log("ERR", "UNKNOWN", "Unknown command: " + cmd);
                        }
                    }
                }
            } catch (IOException e) {
                log("ERR", clientName != null ? clientName : "UNKNOWN", e.getMessage());
            } finally {
                cleanup();
            }
        }

        /**
         * Describes what the handler thread should do when handling a newly connected client
         * @param payload The name of the client that joined, usually
         * @param clientAddr The IP address of the client
         */
        private void handleJoin(final String payload, final String clientAddr) {
            clientName = payload;

            // Record both the assigned handler object and the join time for each client
            clients.put(clientName, this);
            connectTimes.put(clientName, Instant.now());
            log("CONNECT", clientName, "Connected from " + clientAddr);
            sendMessage("ACK:" + clientName + ":Welcome");
        }

        /**
         * Describes what the handler thread should do to process a calculation request from a client
         * @param payload An input string in the format {@code <ClientName>:<ArithmeticExpression>}
         */
        private void handleCalc(final String payload) {
            final String[] parts = payload.split(":", 2);
            if (parts.length != 2) {
                sendMessage("ERR:Invalid Expression Format");
                log("ERR", clientName, "CALC missing expression");
                return;
            }
            final String expr = parts[1];
            requestQueue.offer(new CalcRequest(clientName, expr, this));    // Add the calc request to the queue of calc requests
        }

        /**
         * Describes what the handler thread should do when a client disconnects from the server
         */
        private void cleanup() {
            if (clientName != null) {
                clients.remove(clientName);
                final Instant start = connectTimes.remove(clientName);
                final long secs = start != null ? Duration.between(start, Instant.now()).getSeconds() : 0;  // The number of seconds the client was connected to the server
                log("DISCONNECT", clientName, "Client disconnected after " + secs + " seconds");
            }
            try {
                socket.close();
            } catch (IOException ignored) {}
        }

        /**
         * Utility function for sending messages from the server to clients
         * @param msg The message to be sent to the connected client
         */
        private void sendMessage(final String msg) {
            out.println(msg);
        }
    }
}
