import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class ConnectionMonitoringRunnable implements Runnable {
    private boolean isStopRequested = false;
    private final ReentrantLock lock = new ReentrantLock();

    public void requestStop() {
        try {
            lock.lock();
            this.isStopRequested = true;
        } finally {
            lock.unlock();
        }
    }

    public boolean getIsStopRequested() {
        try {
            lock.lock();
            return this.isStopRequested;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void run() {
        System.out.println( "\t" + Thread.currentThread().getName() + " started.");
        while( !this.getIsStopRequested() ) {
            System.out.println( "\t" + Thread.currentThread().getName() + " is checking for unexpected disconnects." );

            for(Player player: GameServer.getLoggedInPlayers()) {
                // To do
                //System.out.println(player.getUsername() + " disconnected unexpectedly.");
                System.out.println( "\t" + player.getUsername() + " is active." );
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println( "\t" + Thread.currentThread().getName() + " stopped.");
    }
}
