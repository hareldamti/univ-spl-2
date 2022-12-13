package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.LinkedList;

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
    private Object pressedSlotLock;
    private Integer pressedSlot;
    private LinkedList<Integer> tokenPlacements;


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
        this.pressedSlotLock = new Object();
        this.tokenPlacements = new LinkedList<Integer>();
        this.dealer = dealer;

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            synchronized(pressedSlotLock){
                System.out.printf("thread: %s\tpressedSlot: %s\n", Thread.currentThread().getName(), pressedSlot);
                while(pressedSlot == null)
                    try{ pressedSlotLock.wait(); } catch(InterruptedException ignored){}
                toggleToken(pressedSlot);
                pressedSlot = null;
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n\n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                // TODO implement player key press simulator
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if(table.slotToCard[slot] != null){
            synchronized(pressedSlotLock)
            {
                pressedSlot = slot;
                pressedSlotLock.notifyAll();
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
        synchronized(tokenPlacements) {
            if (tokenPlacements.contains(slot)) {
                tokenPlacements.remove(tokenPlacements.indexOf(slot));
                table.removeToken(id, slot);
            }
            else if (tokenPlacements.size() < 3) {
                tokenPlacements.add(slot);
                table.placeToken(id, slot);
            }
            if (tokenPlacements.size() == 3) {
                synchronized(dealer){
                    dealer.addToCheckList(id);
                    dealer.notifyAll();
                }
                // TODO: Notify dealer and update with our tokens
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
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
    }

    public int getScore() {
        return score;
    }

    /**
     * Used by the dealer to access a player's token placement list
     * 
     * @return integer array representing the token placements
     */
    public int[] getTokenPlacements(){
        int[] return_values = new int[tokenPlacements.size()];
        for(int i = 0; i < return_values.length; i++){
            return_values[i] = tokenPlacements.get(i);
            //if(env.DEBUG) System.out.println(String.format("%" + env.config.featureCount + "s", Integer.toString(table.slotToCard[return_values[i]], env.config.featureSize)).replace(' ', '0'));
        }
        //if(env.DEBUG) System.out.println("");
        return return_values;
    }
}
