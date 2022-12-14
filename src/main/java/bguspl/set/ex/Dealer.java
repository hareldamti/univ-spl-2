package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.UtilImpl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Locale.Category;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.tree.TreeNode;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    private Thread[] playerThreads;
    /**
     * Utils
     */
    private long timerStart;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * Queue of players' set check requests
     */
    private ArrayDeque<Integer> setRequests;

    /**
     * Instance of an object that tracks real-time penalties state for all players
     */
    public Penalties penalties;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.playerThreads = new Thread[players.length];
        this.setRequests = new ArrayDeque<>(players.length);
        this.penalties = new Penalties(players.length);

        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        System.out.println("Info: creating players threads...\n");
        for (int i=0; i < players.length; i++) {
            playerThreads[i] = new Thread(this.players[i]);
            playerThreads[i].start();
        }
        
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }

        for (int i = playerThreads.length - 1; i >= 0; i--)
            try{ playerThreads[i].join(); } catch (InterruptedException ignored) {}

        announceWinners();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        timerStart = System.currentTimeMillis();
        reshuffleTime = timerStart + env.config.turnTimeoutMillis;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            //checking if we woke up because of players' notify
            synchronized(setRequests){
                checkSets();
            }
            placeCardsOnTable();
            updateTimerDisplay(false);
            
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        for (int i = players.length - 1; i >= 0; i--) {
                players[i].terminate();
                playerThreads[i].interrupt();
        }
        terminate = true;

    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable() {
        // TODO implement
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        synchronized (table.playersTokens) {
            Random random = new Random();
            for (int slot = 0; slot < table.slotToCard.length; slot++) {
                if (table.slotToCard[slot] == null) {
                    synchronized (table.playersTokens) {
                        for (int id = 0; id < table.playersTokens.size(); id++) {
                            ArrayList<Integer> playerTokens = table.playersTokens.get(id);
                            if (playerTokens.contains(slot)) table.toggleToken(id, slot);
                        }
                    }
                    int chosenCardIndex = random.nextInt(deck.size());
                    int chosenCard = deck.remove(chosenCardIndex);
                    table.placeCard(chosenCard, slot);
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        long extraMillis = 1000 + (timerStart - System.currentTimeMillis()) % 1000;
        synchronized (setRequests) { try {setRequests.wait(extraMillis);} catch (InterruptedException ignored) {} }

        if (env.DEBUG) {
            for (Thread playerThread : playerThreads) {
                //System.out.printf("%s: %s\n",playerThread.getName(),playerThread.getState());
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long countdown = (reshuffleTime - System.currentTimeMillis() + 999) / 1000 * 1000;
        boolean warn = countdown < env.config.turnTimeoutWarningMillis;
        env.ui.setCountdown(countdown, warn);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized (table.playersTokens) {
            for (ArrayList<Integer> playerTokens : table.playersTokens) {
                //TODO: remove all tokens
            }
            for (int slot = 0; slot < table.slotToCard.length; slot++) {
                if (table.slotToCard[slot] != null) {
                    deck.add(table.slotToCard[slot]);
                    table.removeCard(slot);
                }
            }
        }
    }

    /**
     * Used by the players to add themselves to the dealer request queue
     * 
     * @param playerId
     */
    public void addSetRequest(int playerId){
        synchronized (setRequests) {
            setRequests.add(playerId);
            setRequests.notifyAll();
        }
    }

    /**
     * Iterates through the check list to decide if a legal set was declared
     * 
     */
    private void checkSets(){
        Integer requestPlayerId;
        do {
            synchronized (setRequests) {
                requestPlayerId = setRequests.pollFirst();
            }
            if (requestPlayerId != null) {
                Player player = players[requestPlayerId];
                List<Integer> tokenPlacements;
                synchronized(table.playersTokens) { tokenPlacements = table.playersTokens.get(requestPlayerId); }
                synchronized(tokenPlacements) {
                    int[] chosenCards = new int[tokenPlacements.size()];
                    boolean nullInSet = false;
                    for (int i = 0; i < tokenPlacements.size(); i++){
                        nullInSet = table.slotToCard[tokenPlacements.get(i)] == null;
                        if (nullInSet) break;
                        chosenCards[i] = table.slotToCard[tokenPlacements.get(i)];
                    }
                
                    if (nullInSet) {
                        //TODO: handle
                    }

                    else if(env.util.testSet(chosenCards)){
                        player.point();
                        if(env.DEBUG) System.out.println("set found\n");

                        //TODO add low penalty
                        //TODO clear player tokenList
                        //TODO clear set from table and place new cards
                    }

                    else {
                        if(env.DEBUG) System.out.println("set not found\n");
                        synchronized(penalties){penalties.setPenalty(requestPlayerId);}
                        //TODO add heavy penalty
                    }
                    synchronized (player) { player.notifyAll(); }
                    
                }
            }
        }
        while (requestPlayerId != null);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
    }
}
