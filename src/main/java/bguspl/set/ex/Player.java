package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.Random;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ArrayBlockingQueue;
/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 * @inv 3 >= tokenPlacements.size() >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * Dealer instance
     */

     private final Dealer dealer;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * UI slot choises
     */
    private ArrayBlockingQueue<Integer> pressedSlots;

    /**
     * Required freeze time for the player.
     */
    public volatile AtomicLong penaltySec;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.pressedSlots = new ArrayBlockingQueue<Integer>(3);
        table.playersTokens.add(new ArrayList<Integer>(3));
        this.dealer = dealer;
        this.penaltySec = new AtomicLong(0);

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        playerThread = Thread.currentThread();
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            synchronized(pressedSlots){
                while(pressedSlots.isEmpty())
                    try{ pressedSlots.wait(); } catch (InterruptedException interrupted){ break; }
                if (!pressedSlots.isEmpty()) 
                    toggleToken(pressedSlots.poll());
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                Random random = new Random();
                toggleToken(random.nextInt(12));
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        if (!human) aiThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (!human) return;
        if(playerThread.getState() != State.TIMED_WAITING) {
            synchronized(pressedSlots)
            {
                try {pressedSlots.add(slot);} catch (IllegalStateException ignored) {} 
                pressedSlots.notifyAll();
            }
        }
    }

    /**
     * This method is called when the player thread recognized a key was pressed and opts to place/remove a token
     * 
     * @param slot - the slot corresponding to the key pressed.
     * 
     */
    private void toggleToken(int slot) {
        if (table.tokensLock.playerTryLock()) {
            boolean addRequest = table.toggleToken(id, slot);
            table.tokensLock.playerUnlock();
            if (addRequest) {
                synchronized(this) {
                    dealer.addSetRequest(id);
                    try{ wait(); } catch (InterruptedException ignored) {}
                    penalty();
                }
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        long requiredPenalty;
        long resetPenalty = 0;
        do {
            requiredPenalty = penaltySec.get();
        } while (!penaltySec.compareAndSet(requiredPenalty, resetPenalty));
        if (requiredPenalty > 0) {
            try {
                for (long leftPenalty=requiredPenalty; leftPenalty>0; leftPenalty-=1000) {
                    env.ui.setFreeze(id, leftPenalty);
                    Thread.sleep(1000);
                }
                env.ui.setFreeze(id, 0);
            } catch (InterruptedException ignored) {};
        }
    }

    public int score() {
        return score;
    }
}