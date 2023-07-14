import java.util.List;
import java.util.Objects;

public abstract class Game {
    protected final List<Player> players;
    protected final int gameIndex;
    protected final int[] roundsWonByPlayer;

    public Game(List<Player> players, int gameIndex) {
        this.players = players;
        this.gameIndex = gameIndex;
        this.roundsWonByPlayer = new int[players.size()];
    }

    protected void updatePlayersFile() {
        List<Player> playerList = GameServer.getRegisteredPlayers();
        for( Player player: this.players ) {
            for (Player registeredPlayer : playerList) {
                if (Objects.equals(registeredPlayer.getUsername(), player.getUsername())) {
                    registeredPlayer.setRankPoints(player.getRankPoints());
                }
            }
        }
        GameServer.savePlayers(playerList);
    }

    public abstract void start();
}