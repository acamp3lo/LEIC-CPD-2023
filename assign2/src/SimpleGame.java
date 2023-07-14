import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class SimpleGame extends Game {
    private final int numberOfRounds;

    public SimpleGame(List<Player> players, int gameIndex) {
        super(players, gameIndex);
        this.numberOfRounds = players.size();
    }

    @Override
    public void start() {
        System.out.println("Game " + this.gameIndex + " (Simple) started.");
        for (int i = 1; i <= this.numberOfRounds; i++) {
            // Number between 50..99
            int upperBound = (int) (Math.random() * (50)) + 49;
            // Number between 0..49
            int lowerBound = (int) (Math.random() * (49));

            // Start the round
            startRound(i, upperBound, lowerBound);
        }
    }

    private void startRound(int roundNumber, int upperBound, int lowerBound) {
        // Generate random number between lowerBound and upperBound
        int targetNumber = (int) (Math.random() * (upperBound - lowerBound)) + lowerBound;

        int[] guesses = new int[this.players.size()];
        boolean[] hasGuessed = new boolean[this.players.size()];
        int numberOfGuesses = 0;

        while (numberOfGuesses < this.players.size()) {
            // Send round message to all clients
            for (Player player : this.players) {
                String message = "\nRound " + roundNumber + " of " + this.numberOfRounds + ":\n";
                message += "The target interval is " + lowerBound + ".." + upperBound + ".\n";
                try {
                    player.getSocketOutputStream().writeUTF(message);
                } catch(IOException e) {
                    System.err.println("Failed to send Round start message to player " + player.getUsername() + ".");
                    e.getMessage();
                }
            }

            for( int i = 0; i < this.players.size(); i++ ) {
                // Send turn message for clients
                for( Player player: this.players ) {
                    try {
                        if( Objects.equals(player.getUsername(), this.players.get(i).getUsername()) ) {
                            player.getSocketOutputStream().writeUTF( "Its your turn guessing.\n> " );
                        } else {
                            player.getSocketOutputStream().writeUTF( this.players.get(i).getUsername() + " is guessing...\n" );
                        }
                    } catch(IOException e) {
                        System.err.println("Failed to send turn message to player " + player.getUsername() + ".");
                        e.getMessage();
                    }
                }

                // Read guess from player i
                if( !hasGuessed[i] ) {
                    int guess;
                    try {
                        guess = this.players.get(i).getSocketInputStream().readInt();
                        // Validates guess
                        while( !( guess>=lowerBound && guess<=upperBound ) ) {
                            this.players.get(i).getSocketOutputStream().writeUTF("Please select a value within the given range.");
                            guess = this.players.get(i).getSocketInputStream().readInt();
                        }
                        this.players.get(i).getSocketOutputStream().writeUTF("OK");

                        int distance = Math.abs(guess - targetNumber);
                        System.out.print("Game " + this.gameIndex + " - " + "Round " + roundNumber + " of " + this.numberOfRounds + ": ");
                        System.out.println(this.players.get(i).getUsername() + " guessed " + guess + ". Distance from target: " + distance + " (target=" + targetNumber + ").");
                    } catch (IOException e) {
                        System.err.println("Failed to receive guess from player " + this.players.get(i).getUsername() + ".");
                        hasGuessed[i] = true;
                        numberOfGuesses++;
                        continue;
                    }
                    guesses[i] = guess;
                    hasGuessed[i] = true;
                    numberOfGuesses++;
                }
            }
        }

        // Determine the round winner
        int closestGuess = Integer.MAX_VALUE;
        int roundWinnerIndex = -1;
        for (int i = 0; i < this.players.size(); i++) {
            int distance = Math.abs(guesses[i] - targetNumber);
            if (distance < closestGuess) {
                closestGuess = distance;
                roundWinnerIndex = i;
            }
        }

        // Send round result to all clients
        for (int i = 0; i < this.players.size(); i++) {
            try {
                int distance = Math.abs(guesses[i] - targetNumber);
                if (i == roundWinnerIndex) {
                    this.roundsWonByPlayer[i]++;
                    this.players.get(i).getSocketOutputStream().writeUTF("You won round " + roundNumber + ". The target number was " + targetNumber + " and you were the closest! You failed by " + distance + ".");
                } else {
                    this.players.get(i).getSocketOutputStream().writeUTF( this.players.get(roundWinnerIndex).getUsername() + " won round " + roundNumber + ". The target number was " + targetNumber + " and your guess was " + guesses[i] + ". You were " + distance + " away from the target.");
                }
            } catch (IOException e) {
                System.err.println("Failed to send round result to player " + this.players.get(i).getUsername() + ".");
            }
            if( roundNumber == numberOfRounds ) {
                try {
                    this.players.get(i).getSocketOutputStream().writeUTF("\nGame ended. You won " + this.roundsWonByPlayer[i] + " of " + this.numberOfRounds + " rounds.");
                    this.players.get(i).getSocketOutputStream().writeUTF("SIMPLE");
                } catch(IOException e) {
                    System.err.println("Failed to send game ended status to player " + this.players.get(i).getUsername() + ".");
                }
            } else {
                try {
                    this.players.get(i).getSocketOutputStream().writeUTF("Continuing game.");
                } catch(IOException e) {
                    System.err.println("Failed to send continuing game status to player " + this.players.get(i).getUsername() + ".");
                }
            }
        }
    }
}