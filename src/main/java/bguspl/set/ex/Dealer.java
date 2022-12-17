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
 *
 * @inv setRequests.size() <= players.length
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
     * A queue of pending set requests from players
     */
    private ArrayDeque<Integer> setRequests;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.playerThreads = new Thread[players.length];
        this.setRequests = new ArrayDeque<>(players.length);

        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        
        for (int i=0; i < players.length; i++) {
            playerThreads[i] = new Thread(this.players[i]);
            playerThreads[i].start();
        }

        table.tokensLock.dealerLock();
        placeCardsOnTable();
        table.tokensLock.dealerUnlock();
        
        while (!shouldFinish()) {
            timerLoop();
            updateTimerDisplay(false);
            // i.e if mod = timed rounds
            if (env.config.turnTimeoutMillis > 0) {
                table.tokensLock.dealerLock();
                removeAllCardsFromTable();
                placeCardsOnTable();
                table.tokensLock.dealerUnlock();
            }
        }
        terminate();

        for (int i = playerThreads.length - 1; i >= 0; i--)
            try{ playerThreads[i].join(); } catch (InterruptedException ignored) {}

        announceWinners();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        timerStart = System.currentTimeMillis();
        reshuffleTime = timerStart + env.config.turnTimeoutMillis + 1000;
        
        if (env.config.turnTimeoutMillis > 0) {
            while (!terminate && System.currentTimeMillis() < reshuffleTime) {
                sleepUntilWokenOrTimeout();
                updateTimerDisplay(false);
                synchronized(setRequests){
                    checkSets();
                }
            }
        }
        else {
            while(!shouldFinish()) {
                boolean reset = false;
                synchronized(setRequests){
                    if (setRequests.size() == 0)
                        sleepUntilWokenOrTimeout();
                    reset = checkSets();
                }
                
                boolean noSetsAvailable = env.util.findSets(
                    Arrays.stream(table.getCardsOnTable()).collect(Collectors.toList()), 1
                    ).size() == 0;

                if(noSetsAvailable){
                    table.tokensLock.dealerLock();
                    removeAllCardsFromTable();
                    placeCardsOnTable();
                    table.tokensLock.dealerUnlock();
                }
                reset = reset || noSetsAvailable;
                if (env.config.turnTimeoutMillis == 0) updateTimerDisplay(reset);
            }
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
        List<Integer> fullDeck = new ArrayList<Integer>(deck);
        for(int i = 0; i < env.config.rows * env.config.columns; i++)
            if(table.slotToCard[i] != null) 
                fullDeck.add(table.slotToCard[i]);
        return terminate || env.util.findSets(fullDeck, 1).size() == 0;
    }
    

    /**
     * Removes specific cards from the table and discards them.
     * 
     * @param slots: slots placements to remove cards from
     */
    private void removeCardsFromTable(List<Integer> slots) {
        List<Integer> slotsCopy = new ArrayList<Integer>(slots);
        for(int slot : slotsCopy) {
            clearTokens(slot);
            table.removeCard(slot);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Random random = new Random();
        for (int slot = 0; slot < table.slotToCard.length; slot++) {
            if (table.slotToCard[slot] == null) {
                clearTokens(slot);
                if (deck.size() > 0) {
                    int chosenCardIndex = random.nextInt(deck.size());
                    int chosenCard = deck.remove(chosenCardIndex);
                    table.placeCard(chosenCard, slot);
                }
            }
        }
    }

    /**
     * Clears any token placements from a specific slot
     * @param slot: The slot to remove tokens from
     */
    private void clearTokens(int slot){
        for (int id = 0; id < table.playersTokens.size(); id++) {
            ArrayList<Integer> playerTokens = table.playersTokens.get(id);
            if (playerTokens.contains(slot)) 
                table.toggleToken(id, slot);
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        long extraMillis = 1000 + (timerStart - System.currentTimeMillis()) % 1000;
        if (env.config.turnTimeoutMillis > 0) {
            long countdown = (reshuffleTime - System.currentTimeMillis() - 1);
            boolean warn = countdown < env.config.turnTimeoutWarningMillis;
            if (warn) extraMillis = 10 + (timerStart - System.currentTimeMillis()) % 10;
        }
        if (env.config.turnTimeoutMillis < 0) extraMillis = 0;
        synchronized (setRequests) { try {setRequests.wait(extraMillis);} catch (InterruptedException ignored) {} }
        
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(env.config.turnTimeoutMillis > 0) {
            long countdown = (reshuffleTime - System.currentTimeMillis() - 1);
            boolean warn = countdown < env.config.turnTimeoutWarningMillis;
            env.ui.setCountdown(countdown, warn);
        }
        else {
            if (reset) timerStart = System.currentTimeMillis();
            env.ui.setElapsed(System.currentTimeMillis() - timerStart);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int slot = 0; slot < table.slotToCard.length; slot++) {
            if (table.slotToCard[slot] != null) {
                clearTokens(slot);
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }
        }
    }

    /**
     * Used by the players to add themselves to the dealer request queue
     * 
     * @param playerId: requesting player
     */
    public void addSetRequest(int playerId){
        synchronized (setRequests) {
            setRequests.add(playerId);
            setRequests.notifyAll();
        }
    }

    /**
     * Handles set requests from players
     * @return:  wether a legal set was found among the requests
     */
    private boolean checkSets(){
        Integer requestPlayerId;
        boolean foundSets = false;
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
                    boolean illegalSet = tokenPlacements.size() < 3;
                    for (int i = 0; !illegalSet && i < tokenPlacements.size(); i++){
                        illegalSet = table.slotToCard[tokenPlacements.get(i)] == null;
                        if (!illegalSet)
                            chosenCards[i] = table.slotToCard[tokenPlacements.get(i)];
                    }
                    if (illegalSet) {
                    }
                    else if(env.util.testSet(chosenCards)){
                        player.point();
                        
                        table.tokensLock.dealerLock();
                        removeCardsFromTable(tokenPlacements);
                        placeCardsOnTable();
                        table.tokensLock.dealerUnlock();

                        penalizePlayer(requestPlayerId, env.config.pointFreezeMillis);
                        foundSets = true;
                    }
                    else {
                        penalizePlayer(requestPlayerId, env.config.penaltyFreezeMillis);
                    }
                    synchronized (player) { player.notifyAll(); }
                }
            }
        }
        while (requestPlayerId != null);
        return foundSets;
    }

    /**
     * Set the player's penalty field to the required duration
     * 
     * @param id:      player's id
     * @param penalty  player's penalty
     */
    void penalizePlayer(int id, long penalty) {
        Player player = players[id];
        long currentPenalty;
        do {
            currentPenalty = player.penaltySec.get();
        } while(!player.penaltySec.compareAndSet(currentPenalty, penalty));
    }


    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        ArrayList<Integer> scores = new ArrayList<Integer>();
        int currentMax = 0;
        for(int i = 0; i < players.length; i++){
            if(players[i].score() > currentMax) {
                scores = new ArrayList<Integer>();
                scores.add(i);
                currentMax = players[i].score();
            }
            else if(players[i].score() == currentMax) scores.add(i);
        }
        int[] winners = new int[scores.size()];
        for(int i = 0; i < winners.length; i++)
            winners[i] = scores.get(i);

        env.ui.announceWinner(winners);
    }

    /**
     * setRequests getter. for testing
     * @return setRequests
     */
    public ArrayDeque<Integer> getSetRequests() {
        return setRequests;
    }

    /**
     * players getter. for testing
     * @return players
     */
    public Player[] getPlayers() {
        return players;
    }
}
