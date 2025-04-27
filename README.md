# networkcalc
NetworkCalc: a client-server application using Java sockets that allows clients to send arithmetic expressions to the server, which evaluates and returns the computed result. Calc is slang for calculator btw.

## Messaging protocol
The app uses the following message types for its protocol to communicate data between client and server. Keep in mind that each message is implicitly terminated with a newline character `\n` to clearly mark its end.

### 1. Protocol Message Types

#### a. **Client-to-Server Messages**

- **Connection Request (JOIN):**
  - **Purpose:** The client notifies the server about its connection and identifies itself by name.
  - **Format:**  
    `JOIN:<ClientName>`
  - **Example:**  
    `JOIN:Alice`

- **Calculation Request (CALC):**
  - **Purpose:** The client sends an arithmetic expression for evaluation. The expression must include at least two operators from the set `+, -, *, /, %`.
  - **Format:**  
    `CALC:<ClientName>:<ArithmeticExpression>`
  - **Example:**  
    `CALC:Alice:12+7*3`

- **Disconnection Request (LEAVE):**
  - **Purpose:** The client informs the server that it is disconnecting.
  - **Format:**  
    `LEAVE:<ClientName>`
  - **Example:**  
    `LEAVE:Alice`

#### b. **Server-to-Client Messages**

- **Acknowledgment (ACK):**
  - **Purpose:** The server confirms a successful connection upon receiving a JOIN message.
  - **Format:**  
    `ACK:<ClientName>:<ServerMessage>`
  - **Example:**  
    `ACK:Alice:Welcome`

- **Calculation Response (RES):**
  - **Purpose:** The server returns the result of the arithmetic computation.
  - **Format:**  
    `RES:<ClientName>:<Result>`
  - **Example:**  
    `RES:Alice:33`

- **Error Message (ERR):**
  - **Purpose:** In case of an error (e.g., invalid format, computation error), the server informs the client of the issue.
  - **Format:**  
    `ERR:<ErrorDescription>`
  - **Example:**  
    `ERR:Invalid Expression Format`

### 2. Server Logging Format

The server's log entries should follow a consistent timestamped format as enumerated below.

- **Log Entry Format:**
  - **General Format:**
    `[YYYY-MM-DD hh:mm:ss] <EVENT> - <ClientName>: <Details>`
  
- **Examples:**
  - **Connection Log:**
    `[2025-04-15 14:30:25] CONNECT - Alice: Connected from 127.0.0.1:54321`
  - **Calculation Request Log:**
    `[2025-04-15 14:31:00] CALC_REQUEST - Alice: Expression received: 12+7*3`
  - **Calculation Response Log:**
    `[2025-04-15 14:31:05] CALC_RESPONSE - Alice: Result computed: 33`
  - **Disconnection Log:**
    `[2025-04-15 14:32:00] DISCONNECT - Alice: Client disconnected after 33 seconds`
