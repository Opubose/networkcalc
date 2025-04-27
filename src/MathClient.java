import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.*;

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

    public void start() throws IOException, InterruptedException {
        // Open connection and perform JOIN handshake
        connect();
        awaitAck();

        // Start background reader
        readerExecutor.execute(this::readLoop);

        final int expr_count = MIN_EXPRESSION_COUNT + RNG.nextInt(2);   // Send between 3 and 5 total arithmetic expressions 

        // Schedule random expression sends
        for (int i = 0; i < expr_count; i++) {
            int delay = RNG.nextInt(MAX_DELAY_SECONDS) + 1;
            scheduler.schedule(this::sendRandomExpression, delay, TimeUnit.SECONDS);
        }
        // Schedule graceful leave after max delay + buffer
        scheduler.schedule(this::sendLeave, MAX_DELAY_SECONDS + 2, TimeUnit.SECONDS);

        // Wait until leave processed
        while (running) {
            Thread.sleep(100);
        }
    }

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

    private void sendRandomExpression() {
        final String expr = buildRandomExpression();
        sendMessage("CALC:" + clientName + ":" + expr);
        System.out.println("[" + LocalDateTime.now().format(TS) + "] Sent: " + expr);
    }

    private String buildRandomExpression() {
        final int terms = RNG.nextInt(6) + 3; // Between 3 to 8 operands
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < terms; i++) {
            sb.append(RNG.nextInt(100) + 1);    // Generate operand
            if (i < terms - 1) {
                sb.append(randomOperator());
            }
        }
        return sb.toString();
    }

    private char randomOperator() {
        return ops[RNG.nextInt(ops.length)];
    }

    private void sendLeave() {
        sendMessage("LEAVE:" + clientName);
        running = false;
    }

    private void sendMessage(String msg) {
        if (out != null) out.println(msg);
    }

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
