import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MathClient connects to MathServer, sends JOIN, periodically dispatches random
 * arithmetic expressions, handles server responses, and cleanly disconnects.
 */
public class MathClient {
    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    private static final int MIN_EXPRESSION_COUNT = 3;
    private static final int MAX_DELAY_SECONDS = 10;
    private static final Random RNG = new Random();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final char[] ops = {'+', '-', '*', '/', '%'};

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String clientName;
    private volatile boolean running = true;

    /**
     * Main entry point for the client application. Creates a client instance,
     * starts it, and ensures proper shutdown.
     * 
     * @param args Command-line arguments (not used)
     */
    public static void main(String[] args) {
        MathClient client = new MathClient();
        try {
            client.start();
        } catch (IOException | InterruptedException e) {
            System.err.println("[" + LocalDateTime.now().format(TS) + "] Error: " + e.getMessage());
        } finally {
            client.shutdown();
        }
    }

    /**
     * Starts the client, connecting to the server, performing the JOIN handshake,
     * scheduling random expression sends, and waiting until all operations complete.
     * 
     * @throws IOException If there's an error with network communication
     * @throws InterruptedException If the thread is interrupted while waiting
     */
    public void start() throws IOException, InterruptedException {
        // Open connection and perform JOIN handshake
        connect();
        awaitAck();

        // Start background reader
        readerExecutor.execute(this::readLoop);

        final int expr_count = MIN_EXPRESSION_COUNT + RNG.nextInt(6);   // Generate and send between 3 and 8 (inclusive) total arithmetic expressions 

        // Schedule random expression sends
        for (int i = 0; i < expr_count; i++) {
            final int delay = RNG.nextInt(MAX_DELAY_SECONDS) + 1;
            scheduler.schedule(this::sendRandomExpression, delay, TimeUnit.SECONDS);
        }

        // Schedule graceful leave
        final int lifetime = RNG.nextInt(MAX_DELAY_SECONDS + 2) + 10;  // Each client's lifetime will be in the range [10, MAX_DELAY_SECONDS + 1] seconds
        scheduler.schedule(this::sendLeave, lifetime, TimeUnit.SECONDS);

        // Wait until leave processed
        while (running) {
            Thread.sleep(100);
        }
    }

    /**
     * Establishes a connection to the server, initializes the input/output streams,
     * prompts for a client name, and sends the JOIN message.
     * 
     * @throws IOException If there's an error with network communication or user input
     */
    private void connect() throws IOException {
        socket = new Socket(HOST, PORT);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        // Prompt for name
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter your name: ");
        clientName = console.readLine().trim();
        sendMessage("JOIN:" + clientName);
    }

    /**
     * Waits for and processes the acknowledgment from the server after sending
     * the JOIN message. Blocks until an ACK is received for this client.
     * 
     * @throws IOException If there's an error with network communication
     * @throws InterruptedException If the thread is interrupted while waiting
     */
    private void awaitAck() throws IOException, InterruptedException {
        String response;
        
        // Block until we get an ACK for our client name
        while ((response = in.readLine()) != null) {
            if (response.startsWith("ACK:" + clientName + ":")) {
                System.out.println("[" + LocalDateTime.now().format(TS) + "] " + response);
                return;
            }
        }
        throw new IOException("No ACK received");
    }

    /**
     * Continuously reads and displays messages from the server.
     * Runs in its own thread until the client is shut down or an error occurs.
     */
    private void readLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                System.out.println("[" + LocalDateTime.now().format(TS) + "] " + line);
            }
        } catch (IOException e) {
            if (running) System.err.println("Read error: " + e.getMessage());
        } finally {
            running = false;
        }
    }

    /**
     * Generates a random arithmetic expression and sends it to the server.
     * This method is scheduled to run periodically.
     */
    private void sendRandomExpression() {
        final String expr = buildRandomExpression();
        sendMessage("CALC:" + clientName + ":" + expr);
        System.out.println("[" + LocalDateTime.now().format(TS) + "] Sent: " + expr);
    }

    /**
     * Builds a random arithmetic expression with 3-8 operands and random operators.
     * 
     * @return A string containing a random arithmetic expression
     */
    private String buildRandomExpression() {
        final int terms = RNG.nextInt(8) + 3; // Between 3 and 10 (inclusive) operands
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < terms; i++) {
            sb.append(RNG.nextInt(100));    // Generate integer operand in the range [0, 100)
            if (i < terms - 1) {
                sb.append(randomOperator());
            }
        }
        return sb.toString();
    }

    /**
     * Selects a random arithmetic operator from the available set.
     * 
     * @return A randomly chosen operator character (+, -, *, /, or %)
     */
    private char randomOperator() {
        return ops[RNG.nextInt(ops.length)];
    }

    /**
     * Sends a LEAVE message to the server to indicate the client is disconnecting.
     * Also sets the running flag to false to initiate shutdown.
     */
    private void sendLeave() {
        sendMessage("LEAVE:" + clientName);
        running = false;
    }

    /**
     * Utility function to send a message to the server.
     * 
     * @param msg The message to send
     */
    private void sendMessage(String msg) {
        if (out != null) out.println(msg);
    }

    /**
     * Cleans up resources and shuts down the client.
     * Terminates all threads, closes the socket, and prints a termination message.
     */
    private void shutdown() {
        running = false;
        scheduler.shutdownNow();
        readerExecutor.shutdownNow();
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
        System.out.println("Client terminated.");
    }
}
