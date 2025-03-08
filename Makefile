SRC_DIR = src/
BIN_DIR = bin/
CLASSES = $(shell find $(SRC_DIR) -name "*.java")

all: compile

compile:
	mkdir -p $(BIN_DIR)
	javac -d $(BIN_DIR) $(CLASSES)

run-server:
	java -cp $(BIN_DIR) MathServer

run-client:
	java -cp $(BIN_DIR) MathClient

clean:
	rm -rf $(BIN_DIR)
