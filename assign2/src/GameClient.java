import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.Timer;

public class GameClient {
    private static final int CONSOLE_TIMEOUT = 1200;
    private static Socket SOCKET;

    public static void main(String[] args) {
        try {
            String HOSTNAME = "localhost";
            int PORT = 5000;
            Socket socket = new Socket(HOSTNAME, PORT);
            SOCKET = socket;
            int PING_INTERVAL = 5000;

            clearConsole();
            System.out.println("-----------CONNECTED TO SERVER------------\n");
            Thread.sleep(CONSOLE_TIMEOUT);

            Scanner scanner = new Scanner(System.in);
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

            playerAuthentication(inputStream, outputStream, scanner);

            int numberOfPlayersInGame = gameSetup(inputStream, outputStream, scanner);

            gameStart(inputStream, outputStream, scanner, numberOfPlayersInGame);

        } catch (IOException e) {
            System.out.println("\nClient exception: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (SOCKET != null) {
                try {
                    // Close the socket
                    SOCKET.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void afterGame(DataInputStream inputStream, DataOutputStream outputStream, Scanner scanner) {
        printAfterGameMenu();

        int choice = scanner.nextInt();
        // Consumes the \n left by scanner.nextInt()
        scanner.nextLine();

        // Validate input
        while( (choice != 1) && (choice != 2) ) {
            System.out.println("ERROR - Invalid input. Please select a valid option.");
            printAfterGameMenu();
            choice = scanner.nextInt();
            // Consumes the \n left by scanner.nextInt()
            scanner.nextLine();
        }

        if( choice == 1 ) {
            try {
                // Send play again notice to server
                outputStream.writeInt(choice);
                Thread.sleep(CONSOLE_TIMEOUT);
            } catch(InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                System.err.println("Failed to send play again notice to server.");
            }
            clearConsole();
            int numberOfPlayersInGame = gameSetup(inputStream, outputStream, scanner);
            gameStart(inputStream, outputStream, scanner, numberOfPlayersInGame);
        } else {
            try {
                // Send disconnect notice to server
                outputStream.writeInt(choice);
                SOCKET.close();
            } catch(IOException e) {
                System.err.println("Failed to send disconnect notice to server.");
            }
        }
    }

    private static int gameSetup(DataInputStream inputStream, DataOutputStream outputStream, Scanner scanner) {
        // Handling game type selection
        gameTypeSelection(outputStream, scanner);

        try {
            // Gets queue validation
            System.out.println(inputStream.readUTF());
        } catch(IOException e) {
            System.err.println("Failed to get queue validation from server.");
        }

        String message = "";
        try {
            // Gets & prints game creation message
            message = inputStream.readUTF();
            System.out.println(message);
        } catch(IOException e) {
            System.err.println("Failed to get game creation message.");
        }
        // Gets number of players
        int numberOfPlayersInGame = 0;
        for( char c: message.toCharArray() ) {
            if( Character.isDigit(c) ) {
                numberOfPlayersInGame = Character.getNumericValue(c);
            }
        }
        return numberOfPlayersInGame;
    }

    private static void gameStart(DataInputStream inputStream, DataOutputStream outputStream, Scanner scanner, int numberOfPlayersInGame) {
        // Play the rounds
        while (true) {
            int count = numberOfPlayersInGame;
            try {
                // Gets & prints round start message
                String message = inputStream.readUTF();
                System.out.print(message);
                // Gets & prints turn message
                message = inputStream.readUTF();
                count--;
                System.out.print(message);
                while( !message.startsWith("Its your turn") ) {
                    message = inputStream.readUTF();
                    System.out.print(message);
                    count--;
                }
            } catch(IOException e) {
                System.err.println("Failed to receive prompt from server.");
            }

            // Gets guess
            int guess = scanner.nextInt();
            // Consumes the \n left by scanner.nextInt()
            scanner.nextLine();

            try {
                // Send guess to server
                outputStream.writeInt(guess);
            } catch(IOException e) {
                System.err.println("Failed to send guess to server.");
            }

            try {
                // Validate guess
                String valMessage = inputStream.readUTF();
                while(valMessage.startsWith("Please select a value within the given range.")) {
                    System.out.println(valMessage);
                    System.out.print("> ");
                    guess = scanner.nextInt();
                    // Consumes the \n left by scanner.nextInt()
                    scanner.nextLine();
                    outputStream.writeInt(guess);
                    valMessage = inputStream.readUTF();
                }
            } catch(IOException e) {
                System.err.println("Failed to validate guess.");
            }

            // Prints other users turns
            while( count > 0 ) {
                try {
                    System.out.print(inputStream.readUTF());
                    count--;
                } catch(IOException e) {
                    e.getMessage();
                }
            }

            try {
                // Receive result from server
                String result = inputStream.readUTF();
                System.out.println(result);
            } catch(IOException e) {
                System.err.println("Failed to receive result from server.");
            }

            try {
                // Checks if game ended
                String message = inputStream.readUTF();
                if( message.startsWith("\nGame ended") ) {
                    System.out.print(message);
                    message = inputStream.readUTF();
                    if( !message.startsWith("SIMPLE") ) {
                        System.out.println(message);
                    }
                    System.out.println();
                    Thread.sleep(CONSOLE_TIMEOUT);
                    afterGame(inputStream, outputStream, scanner);
                    break;
                }
            } catch(IOException e) {
                System.err.println("Failed to receive game status from server.");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void clearConsole() {
        System.out.println("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");
    }

    private static void gameTypeSelection(DataOutputStream outputStream, Scanner scanner) {
        // Print game type selection menu
        printGameSelectionMenu();
        // Gets choice
        int choice = scanner.nextInt();
        // Consumes the \n left by scanner.nextInt()
        scanner.nextLine();
        while( (choice != 1) && (choice != 2) ) {
            System.out.println("ERROR - Invalid input. Please select a valid option.");
            printGameSelectionMenu();
            choice = scanner.nextInt();
            // Consumes the \n left by scanner.nextInt()
            scanner.nextLine();
        }
        // Print selection confirmation
        if( choice == 1 ) {
            System.out.println("Simple game mode selected.");
        } else {
            System.out.println("Ranked game mode selected.");
        }
        try {
            // Sends game type selection to server
            outputStream.writeInt(choice);
            Thread.sleep(CONSOLE_TIMEOUT);
            clearConsole();
        } catch(InterruptedException e) {
            e.printStackTrace();
        } catch(IOException e) {
            System.err.println("Failed to send game type selection to server.");
        }
    }

    private static void playerAuthentication(DataInputStream inputStream, DataOutputStream outputStream, Scanner scanner) throws IOException, InterruptedException {
        // Print authentication menu
        printAuthenticationMenu();
        // Gets choice
        int choice = scanner.nextInt();
        // Consumes the \n left by scanner.nextInt()
        scanner.nextLine();
        // Validate input
        while( (choice != 1) && (choice != 2) ) {
            System.out.println("ERROR - Invalid input. Please select a valid option.");
            printAuthenticationMenu();
            choice = scanner.nextInt();
            // Consumes the \n left by scanner.nextInt()
            scanner.nextLine();
        }
        // Send authentication selection to server
        outputStream.writeInt(choice);
        clearConsole();

        if( choice == 1 ) {     // LOGIN
            // Prints Login Menu & returns username and password
            String[] userData = printLoginMenu(scanner);
            // Sends login user data to server
            outputStream.writeUTF(userData[0]);
            outputStream.writeUTF(userData[1]);
            // Checks for server login validation
            String serverMessage = inputStream.readUTF();
            while(!serverMessage.startsWith("OK")) {
                System.out.println(serverMessage);
                // Prints login menu & returns username and password
                userData = printLoginMenu(scanner);
                // Sends login user data to server
                outputStream.writeUTF(userData[0]);
                outputStream.writeUTF(userData[1]);
                // Gets server validation
                serverMessage = inputStream.readUTF();
            }
            System.out.println(serverMessage);
        } else  {           // REGISTER
            // Prints Register Menu & returns username and password
            String[] userData = printRegistrationMenu(scanner);
            // Sends registration data to server
            outputStream.writeUTF(userData[0]);
            outputStream.writeUTF(userData[1]);
            // Checks for server registration validation
            String serverMessage = inputStream.readUTF();
            while(!serverMessage.startsWith("OK")) {
                System.out.println(serverMessage);
                // Prints registration menu & returns username and password
                userData = printRegistrationMenu(scanner);
                // Sends registration data to server
                outputStream.writeUTF(userData[0]);
                outputStream.writeUTF(userData[1]);
                // Gets server validation
                serverMessage = inputStream.readUTF();
            }
            System.out.println(serverMessage);
        }
        Thread.sleep(CONSOLE_TIMEOUT);
        clearConsole();
    }

    private static void printAfterGameMenu() {
        String menu = "\n----------------GAME OVER-----------------\n";
        menu += "Please select a option:\n";
        menu += "1 - Play again\n";
        menu += "2 - Disconnect\n";
        menu += "> ";
        System.out.print(menu);
    }

    private static void printGameSelectionMenu() {
        String menu = "\n----------------GAME TYPE-----------------\n";
        menu += "Please select a game type:\n";
        menu += "1 - Simple\n";
        menu += "2 - Ranked\n";
        menu += "> ";
        System.out.print(menu);
    }

    private static void printAuthenticationMenu() {
        String menu = "\n--------------AUTHENTICATION--------------\n";
        menu += "Please select one of the options bellow:\n";
        menu += "1 - Login\n";
        menu += "2 - Register\n";
        menu += "> ";
        System.out.print(menu);
    }
    private static String[] printRegistrationMenu(Scanner scanner) {
        String[] userData = new String[2];
        String menu = "-----------------REGISTER-----------------\n";
        menu += "Please enter a username:\n";
        menu += "> ";
        System.out.print(menu);
        userData[0] = scanner.nextLine();
        menu = "Please enter a password:\n";
        menu += "> ";
        System.out.print(menu);
        userData[1] = scanner.nextLine();

        return userData;
    }
    private static String[] printLoginMenu(Scanner scanner) {
        String[] userData = new String[2];
        String menu = "------------------LOGIN-------------------\n";
        menu += "Please enter your username:\n";
        menu += "> ";
        System.out.print(menu);
        userData[0] = scanner.nextLine();
        menu = "Please enter your password:\n";
        menu += "> ";
        System.out.print(menu);
        userData[1] = scanner.nextLine();

        return userData;
    }
}
