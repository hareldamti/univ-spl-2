package bguspl.set.ex;
import java.util.concurrent.atomic.AtomicInteger;
import bguspl.set.Env;

public class Semaphore {
    public AtomicInteger activePlayers;
    public AtomicInteger dealerState; //1: active. -1: waiting. 0: not using
    public Object dealerLock;
    public Semaphore() {
        activePlayers = new AtomicInteger(0);
        dealerState = new AtomicInteger(0);
        dealerLock = new Object();
    }
    public boolean playerTryLock() {
        if (dealerState.get() != 0) return false;
        int current, next;
        do {current = activePlayers.get(); next = current + 1;}
        while (!activePlayers.compareAndSet(current, next));
        System.out.printf("Player Aquiring: %s\n", activePlayers.get());
        
        return true;
    }
    public void playerUnlock() {
        int current, next;
        do {current = activePlayers.get(); next = current - 1;}
        while (!activePlayers.compareAndSet(current, next));
        synchronized(dealerLock) {dealerLock.notifyAll();}
        System.out.printf("Player releasing: %s\n", activePlayers.get());
    }
    public void dealerLock() {
        dealerState.set(-1);
        while (activePlayers.get() > 0) {
            System.out.printf("Dealer waiting %s\n", activePlayers.get());
            synchronized(dealerLock) {try {dealerLock.wait(10);} catch (InterruptedException ignored) {};}
        }
        dealerState.set(1);
    }
    public void dealerUnlock() {
        dealerState.set(0);
    }
};
