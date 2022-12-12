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

    private ArrayDeque<Integer> checkForSets;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.playerThreads = new Thread[players.length];
        this.checkForSets = new ArrayDeque<>(players.length);

        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());

        System.out.println("creating players threads...\n");
        for (int i=0; i<players.length; i++) 
        {
            playerThreads[i] = new Thread(this.players[i]);
        }

        if (env.DEBUG) {
            for (int i=0; i<playerThreads.length; i++) {
                playerThreads[i].start();
                //System.out.printf("%s: %s\n",playerThreads[i].getName(),playerThreads[i].getState());
            }
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
            synchronized(this){
                while(checkForSets.size() > 0){
                    int playerId = checkForSets.pollFirst();
                    int[] playerTokenPlacements = players[playerId].getTokenPlacements();
                    int[] cardsInSlotsOfTokens = new int[playerTokenPlacements.length];
        
                    for(int i = 0;i < playerTokenPlacements.length; i++){
                        cardsInSlotsOfTokens[i] = table.slotToCard[i];
                    }

                    if(env.util.testSet(cardsInSlotsOfTokens)){
                        players[playerId].point();
                        if(env.DEBUG) System.out.println("set found\n");
                        //TODO add low penalty
                        //TODO clear player tokenList
                    }
                    else{
                        if(env.DEBUG) System.out.println("set not found\n");
                        //TODO add heavy penalty
                    }
                }
            }

            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
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
        Random random = new Random();
        for (int slot = 0; slot < table.slotToCard.length; slot++) {
            if (table.slotToCard[slot] == null) {
                int chosenCardIndex = random.nextInt(deck.size());
                int chosenCard = deck.remove(chosenCardIndex);
                table.placeCard(chosenCard, slot);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        long extraMillis = 1000 + (timerStart - System.currentTimeMillis()) % 1000;
        synchronized (this) { try {wait(extraMillis);} catch (InterruptedException ignored) {} }

        if (env.DEBUG) {
            for (Thread playerThread : playerThreads) {
                System.out.printf("%s: %s\n",playerThread.getName(),playerThread.getState());
            }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        env.ui.setCountdown(reshuffleTime - System.currentTimeMillis() - 1, reset);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int slot = 0; slot < table.slotToCard.length; slot++) {
            if (table.slotToCard[slot] != null) {
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }
        }
    }

    /**
     * Used by the players to add themselves to the dealer checklist
     * 
     * @param playerId
     */
    public void addToCheckList(int playerId){
        checkForSets.add(playerId);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
    }
}
