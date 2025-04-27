# networkcalc

**NetworkCalc** is a simple client-server application using Java sockets that allows multiple clients to send arithmetic expressions to a centralized server, which evaluates the expressions and returns the computed results. ("Calc" is slang for calculator, btw.)

This project demonstrates basic networking, concurrency, and protocol design in Java.

## Requirements
- Java 21 or later.

## How to Compile

First, clone or download the project files, then open a terminal or command prompt in the project root directory.

On a Unix system, compile the server and client source files using:

```bash
make
```

On Windows, you may use:

```bash
.\build.bat compile
```

This will produce `MathServer.class` and `MathClient.class` in the `bin/` project sub-directory.

## How to Run

### 1. Start the Server

Unix:
```bash
make run-server
```
Windows:
```bash
.\build.bat run-server
```

This will start the server listening on port `12345`.

> Note: A `logs/` sub-directory will be created automatically, and a fresh `server.log` file will be generated for each server run.

### 2. Start a Client

In a new terminal window, run:

Unix:
```bash
make run-client
```
Windows:
```bash
.\build.bat run-client
```

You will be prompted to enter your name once the client is running. After connecting to the server, the client will randomly generate and send three arithmetic expressions at random intervals to the server. Then, it will disconnect automatically.

You can open multiple clients at once to simulate multiple users connecting and interacting with the server simultaneously.

## Messaging Protocol

The app uses the following message types for communication between client and server.  
Each message is implicitly terminated with a newline character `\n`.

### 1. Protocol Message Types

#### a. Client-to-Server Messages

- Connection Request (JOIN):
  - Format: `JOIN:<ClientName>`
  - Example: `JOIN:Alice`

- Calculation Request (CALC):
  - Format: `CALC:<ClientName>:<ArithmeticExpression>`
  - Example: `CALC:Alice:12+7*3`

- Disconnection Request (LEAVE):
  - Format: `LEAVE:<ClientName>`
  - Example: `LEAVE:Alice`

#### b. Server-to-Client Messages

- Acknowledgment (ACK):
  - Format: `ACK:<ClientName>:<ServerMessage>`
  - Example: `ACK:Alice:Welcome`

- Calculation Response (RES):
  - Format: `RES:<ClientName>:<Result>`
  - Example: `RES:Alice:33`

- Error Message (ERR):
  - Format: `ERR:<ErrorDescription>`
  - Example: `ERR:Invalid Expression Format`

## Server Logging Format

The server writes events to the console and into `logs/server.log` in the following format:

- Log Entry Format: 
  `[YYYY-MM-DD hh:mm:ss] <EVENT> - <ClientName>: <Details>`

- Examples:
  - Connection:
    `[2025-04-15 14:30:25] CONNECT - Alice: Connected from 127.0.0.1:54321`
  - Calculation Request:  
    `[2025-04-15 14:31:00] CALC_REQUEST - Alice: Expression received: 12+7*3`
  - Calculation Response:  
    `[2025-04-15 14:31:05] CALC_RESPONSE - Alice: Result computed: 33`
  - Disconnection:  
    `[2025-04-15 14:32:00] DISCONNECT - Alice: Client disconnected after 120 seconds`
