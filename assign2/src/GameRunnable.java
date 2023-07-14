import java.io.IOException;
import java.util.List;

public class GameRunnable implements Runnable {
    private final List<Player> players;
    private final int gameIndex;
    private final boolean isRanked;

    public GameRunnable(List<Player> players, boolean isRanked) {
        this.players = players;
        this.gameIndex = GameServer.getNumberOfActiveGames();
        this.isRanked = isRanked;
    }

    @Override
    public void run() {
        Game game;
        // Creates game
        if( this.isRanked ) {
            game = new RankedGame(this.players, gameIndex);
            System.out.println("Game " + this.gameIndex + " (Ranked) created with " + this.players.size() + " players.");
        } else {
            game = new SimpleGame(this.players, gameIndex);
            System.out.println("Game " + this.gameIndex + " (Simple) created with " + this.players.size() + " players.");
        }

        // Print useful server information
        GameServer.printServerStatus();

        this.notifyGameStartToPlayers();
        game.start();

        System.out.println("Game " + this.gameIndex + " finished.");

        this.afterGame();
    }

    private void afterGame() {
        GameServer.decrementNumberOfActiveGames();
        for(Player player: this.players) {
            try {
                // Gets client end game choice
                int choice = player.getSocketInputStream().readInt();
                if( choice == 2 ) {
                    GameServer.playerLogout(player);
                } else if( choice == 1 ) {
                    System.out.println(player.getUsername() + " chose to play again.");
                    // Handling game type selection
                    boolean isRankedGame = GameServer.gameTypeSelection(player);
                    // Add player to respective queue
                    if(isRankedGame) {
                        GameServer.addPlayerToRankedQueue(player);
                    } else {
                        GameServer.addPlayerToSimpleQueue(player);
                    }

                    // Print useful server information
                    GameServer.printServerStatus();

                    // Game setup
                    GameServer.setupGame(GameServer.getGameThreadPool(), isRankedGame);
                } else {
                    System.err.println("Unexpected input from player " + player.getUsername() + ".");
                }
            } catch(IOException e) {
                System.err.println("Failed to get endgame choice from player " + player.getUsername() + ".");
            }
        }
    }

    private void notifyGameStartToPlayers() {
        // Notifies the players the game is starting
        for( Player player: this.players ) {
            try {
                player.getSocketOutputStream().writeUTF("\nStarting " + ( (this.isRanked) ? ("Ranked") : ("Simple") ) + " Game with " + players.size() + " players.");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}