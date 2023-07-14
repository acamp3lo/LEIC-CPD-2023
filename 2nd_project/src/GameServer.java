import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class GameServer {
    private static final int PORT = 5000;
    private static final int MAX_THREADS = 8;
    private static final String REGISTRATION_FILE = "players.csv";
    private static final int MIN_RANKED_TEAM_SIZE = 3;
    private static final int MIN_SIMPLE_TEAM_SIZE = 2;
    private static final int MAX_TEAM_SIZE = 8;
    private static final int MAX_PLAYERS = 50;
    private static final List<Player> registeredPlayers = new ArrayList<>();
    private static final List<Player> loggedInPlayers = new ArrayList<>();
    private static final List<Player> simpleGameQueue = new ArrayList<>();
    private static final List<Player> rankedGameQueue = new ArrayList<>();
    private static int numberOfActiveGames = 0;
    private static final ExecutorService gameThreadPool = Executors.newFixedThreadPool(MAX_THREADS);
    private static final ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("\nServer is listening on port " + PORT);
            System.out.println("Waiting for players to connect...");

            loadRegisteredPlayers();

            // Starts Player connection monitoring thread
            ConnectionMonitoringRunnable connectionMonitoringRunnable = new ConnectionMonitoringRunnable();
            Thread connectionMonitoringThread = new Thread( connectionMonitoringRunnable, "Connection Monitoring Thread" );
            connectionMonitoringThread.start();

            //noinspection InfiniteLoopStatement
            while(true) {
                Socket socket = serverSocket.accept();
                if(loggedInPlayers.size() > MAX_PLAYERS) {
                    System.out.println("Server maximum number of players reached.\nRejecting Client connection.");
                    socket.close();
                    continue;
                }
                System.out.println("\nNew player connected: " + socket);

                Player player = new Player(null, null, socket);

                // Player Authentication
                playerAuthentication(player);

                // Handling game type selection
                boolean isRankedGame = gameTypeSelection(player);
                // Add player to respective queue
                if(isRankedGame) {
                    addPlayerToRankedQueue(player);
                } else {
                    addPlayerToSimpleQueue(player);
                }

                // Print useful server information
                printServerStatus();

                // Game setup
                setupGame(gameThreadPool, isRankedGame);
            }
        } catch(IOException e) {
            System.out.println("\nServer exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    protected static void printServerStatus() {
        try {
            lock.lock();
            System.out.println( numberOfActiveGames + " game/s active." );
            System.out.println( loggedInPlayers.size() + " player/s connected to the server." );
            System.out.println( simpleGameQueue.size() + " player/s in the simple game queue." );
            System.out.println( rankedGameQueue.size() + " player/s in the ranked game queue." );
        } finally {
            lock.unlock();
        }
    }

    private static Map<Integer, List<Player>> groupPlayersByRankTier() {
        return rankedGameQueue.stream().collect(Collectors.groupingBy(Player::getRankTier));
    }

    protected static boolean gameTypeSelection(Player player) throws IOException {
        try {
            lock.lock();
            int choice = 0;
            try {
                // Gets game type selection from client
                choice = player.getSocketInputStream().readInt();
            } catch(IOException e) {
                System.err.println("Failed to get game type selection from server.");
            }
            if( choice == 1 ) {
                return false;
            } else if( choice == 2 ) {
                return true;
            } else {
                throw new IOException("Unexpected client game type selection.");
            }
        } finally {
            lock.unlock();
        }
    }

    protected static void setupGame(ExecutorService gameThreadPool, boolean isRankedGame) {
        try {
            lock.lock();
            if(isRankedGame) {
                Map<Integer, List<Player>> groupedRankedPlayers = groupPlayersByRankTier();

                // Prints grouped players by rank tier
                for (Map.Entry<Integer, List<Player>> entry : groupedRankedPlayers.entrySet()) {
                    Integer rankTier = entry.getKey();
                    List<Player> playerGroup = entry.getValue();
                    System.out.print("Ranked game queue: Tier " + rankTier + " -> ");
                    for ( int i = 0; i < playerGroup.size(); i++ ) {
                        if( i == playerGroup.size() - 1 ) {
                            System.out.print(playerGroup.get(i).getUsername());
                        } else {
                            System.out.print(playerGroup.get(i).getUsername() + ", ");
                        }
                    }
                    System.out.println();
                }

                // Starts ranked game
                for (Map.Entry<Integer, List<Player>> entry : groupedRankedPlayers.entrySet()) {
                    List<Player> groupedRankedGameQueue = entry.getValue();
                    while( groupedRankedGameQueue.size() >= MIN_RANKED_TEAM_SIZE ) {
                        // Divide ranked game group into teams & clears it
                        List<List<Player>> teams = splitQueueIntoTeams(groupedRankedGameQueue);
                        // Starts a game for each team
                        for( List<Player> team: teams ) {
                            numberOfActiveGames++;
                            for( Player player: team ) {
                                rankedGameQueue.remove(player);
                            }
                            gameThreadPool.execute( new GameRunnable(team, true) );
                        }
                    }
                }
            } else {
                while( simpleGameQueue.size() >= MIN_SIMPLE_TEAM_SIZE) {
                    // Divide simple game queue into teams & clears it
                    List<List<Player>> teams = splitQueueIntoTeams(simpleGameQueue);

                    // Starts a game for each team
                    for( List<Player> team: teams ) {
                        numberOfActiveGames++;
                        gameThreadPool.execute( new GameRunnable(team, false) );
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private static List<List<Player>> splitQueueIntoTeams( List<Player> queue) {
        List<List<Player>> teams = new ArrayList<>();
        while( queue.size() > MAX_TEAM_SIZE ) {
            teams.add( new ArrayList<>( queue.subList(0, MAX_TEAM_SIZE) ) );
            clearQueue( queue.subList(0, MAX_TEAM_SIZE) );
        }
        if( queue.size() != 0 ) {
            teams.add( new ArrayList<>(queue) );
            clearQueue(queue);
        }
        return teams;
    }

    private static void clearQueue( List<Player> queue ) {
        queue.clear();
    }

    private static void playerAuthentication(Player player) {
        try {
            // Gets authentication selection from client
            int choice = player.getSocketInputStream().readInt();

            String username = player.getSocketInputStream().readUTF();
            String password = player.getSocketInputStream().readUTF();
            if( choice == 1 ) { // LOGIN

                // Player login & login validation
                while (!playerLogin(username, password, player)) {
                    player.getSocketOutputStream().writeUTF("Invalid username, password or user is already logged in. Please try again.");

                    username = player.getSocketInputStream().readUTF();
                    password = player.getSocketInputStream().readUTF();
                }
            } else if( choice == 2 ) {        // REGISTER

                // Player registration & registration validation
                while( !playerRegistration(username, password, player) ) {
                    player.getSocketOutputStream().writeUTF("Username already exists. Please try again.");

                    username = player.getSocketInputStream().readUTF();
                    password = player.getSocketInputStream().readUTF();
                }
            } else {
                throw new IOException("Unexpected client authentication selection.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static void addPlayerToSimpleQueue(Player player) throws IOException {
        try {
            lock.lock();
            simpleGameQueue.add(player);
            player.getSocketOutputStream().writeUTF("You are now queuing for a simple game.\nWaiting for players...");
        } finally {
            lock.unlock();
        }
    }

    protected static void addPlayerToRankedQueue(Player player) throws IOException {
        try {
            lock.lock();
            rankedGameQueue.add(player);
            player.getSocketOutputStream().writeUTF("You are now queuing for a ranked game.\nWaiting for players...");
        } finally {
            lock.unlock();
        }
    }

    private static boolean playerRegistration(String username, String password, Player player) throws IOException {
        for (Player registeredPlayer : registeredPlayers) {
            if (registeredPlayer.getUsername().equals(username)) {
                return false;
            }
        }
        player.setUsername(username);
        player.setPassword(password);
        player.setRankPoints(0);
        registeredPlayers.add(player);
        savePlayers(registeredPlayers);
        loggedInPlayers.add(player);

        System.out.println(player.getUsername() + " was registered successfully. His token is: " + player.getToken() + ".");
        player.getSocketOutputStream().writeUTF("OK - Registration successful.");
        return true;
    }

    protected static void playerLogout(Player player) {
        try {
            lock.lock();
            loggedInPlayers.remove(player);
            System.out.println( player.getUsername() + " disconnected from the server." );

            // Print useful server information
            GameServer.printServerStatus();
        } finally {
            lock.unlock();
        }
    }

    private static boolean playerLogin(String username, String password, Player player) throws IOException {
        for( Player registeredPlayer : registeredPlayers ) {
            if( registeredPlayer.getUsername().equals(username) && registeredPlayer.getPassword().equals(password) ) {
                for( Player loggedInPlayer: loggedInPlayers ) {
                    if(Objects.equals(registeredPlayer.getUsername(), loggedInPlayer.getUsername())) {
                        return false;
                    }
                }

                player.setUsername(registeredPlayer.getUsername());
                player.setPassword(registeredPlayer.getPassword());
                player.setRankPoints(registeredPlayer.getRankPoints());
                loggedInPlayers.add(player);

                System.out.println(registeredPlayer.getUsername() + " logged in. His token is: " + player.getToken() + ".");
                player.getSocketOutputStream().writeUTF("OK - Login successful. Welcome back " + player.getUsername() + ".");
                return true;
            }
        }
        return false;
    }

    protected static void savePlayers(List<Player> players) {
        try {
            lock.lock();
            try (PrintWriter writer = new PrintWriter(new FileWriter(REGISTRATION_FILE))) {
                for (Player player : players) {
                    writer.println(player.getUsername() + "," + player.getPassword() + "," + player.getRankPoints());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } finally {
            lock.unlock();
        }
    }

    private static void loadRegisteredPlayers() {
        try (BufferedReader reader = new BufferedReader(new FileReader(REGISTRATION_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] userData = line.split(",");
                String username = userData[0];
                String password = userData[1];
                int playerRank = Integer.parseInt(userData[2]);
                registeredPlayers.add(new Player(username, password, playerRank));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static void decrementNumberOfActiveGames() {
        try {
            lock.lock();
            numberOfActiveGames--;
        } finally {
            lock.unlock();
        }
    }

    protected static List<Player> getRegisteredPlayers() {
        try {
            lock.lock();
            return registeredPlayers;
        } finally {
            lock.unlock();
        }
    }

    protected static List<Player> getLoggedInPlayers() {
        try {
            lock.lock();
            return loggedInPlayers;
        } finally {
            lock.unlock();
        }
    }

    protected static int getNumberOfActiveGames() {
        try {
            lock.lock();
            return numberOfActiveGames;
        } finally {
            lock.unlock();
        }
    }

    protected static ExecutorService getGameThreadPool() {
        try {
            lock.lock();
            return gameThreadPool;
        } finally {
            lock.unlock();
        }
    }
}