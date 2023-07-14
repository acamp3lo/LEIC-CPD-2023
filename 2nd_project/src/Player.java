import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

public class Player {
    private String username;
    private String password;
    private Socket socket;
    private String token = null;
    private int rankPoints = 0;
    private int rankTier = 0;
    private DataInputStream socketInputStream = null;
    private DataOutputStream socketOutputStream = null;

    public Player(String username, String password, Socket socket) throws IOException {
        this.username = username;
        this.password = password;
        this.socket = socket;
        this.token = this.generateToken();
        this.socketInputStream = new DataInputStream(this.socket.getInputStream());
        this.socketOutputStream = new DataOutputStream(this.socket.getOutputStream());
    }
    public Player(String username, String password, int rank) {   // FOR REGISTRATION ONLY
        this.username = username;
        this.password = password;
        this.rankPoints = rank;
    }

    public String generateToken() {
        SecureRandom secureRandom = new SecureRandom();
        int TOKEN_LENGTH = 10;
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(tokenBytes);

        long timestamp = Instant.now().toEpochMilli();

        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes) + "_" + timestamp;
    }

    public void updateRank(int i) {
        this.rankPoints += i;
        if( this.rankPoints < 0 ) {
            this.rankPoints = 0;
        }
        this.updateRankTier();
    }

    private void updateRankTier() {
        if( this.rankPoints > 500 && this.rankPoints <= 1000 ) {
            this.rankTier = 1;
        } else if( this.rankPoints > 1000 && this.rankPoints <= 1500 ) {
            this.rankTier = 2;
        } else if( this.rankPoints > 1500 && this.rankPoints <= 2500 ) {
            this.rankTier = 3;
        } else if( this.rankPoints > 2500 ) {
            this.rankTier = 4;
        } else {
            this.rankTier = 0;
        }
    }

    // Getters
    public String getUsername() {
        return this.username;
    }
    public String getPassword() {
        return this.password;
    }
    public Socket getSocket() {
        return this.socket;
    }
    public String getToken() {
        return this.token;
    }
    public int getRankPoints() {
        return this.rankPoints;
    }
    public int getRankTier() {
        return this.rankTier;
    }
    public DataInputStream getSocketInputStream() {
        return socketInputStream;
    }
    public DataOutputStream getSocketOutputStream() {
        return socketOutputStream;
    }

    // Setters
    public void setSocket(Socket socket) {
        this.socket = socket;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public void setRankPoints(int rankPoints) {
        this.rankPoints = rankPoints;
        updateRankTier();
    }
}
