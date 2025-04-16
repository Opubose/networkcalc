JAVAC = javac
JAVA = java

SRC_DIR = src
BIN_DIR = bin
SOURCES = $(wildcard $(SRC_DIR)/*.java)
CLASSES = $(SOURCES:$(SRC_DIR)/%.java=$(BIN_DIR)/%.class)

all: compile

compile: $(CLASSES)

$(BIN_DIR)/%.class: $(SRC_DIR)/%.java
	@mkdir -p $(BIN_DIR)
	$(JAVAC) -d $(BIN_DIR) $<

run-server: compile
	$(JAVA) -cp $(BIN_DIR) MathServer

run-client: compile
	java -cp $(BIN_DIR) MathClient

clean:
	rm -rf $(BIN_DIR)
