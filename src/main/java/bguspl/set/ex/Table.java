package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.swing.UIDefaults.ActiveValue;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 * @inv env.config.featureSize >= playerTokens.size() >= for playerTokens in playersTokens
 *
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;
    
    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * Maintaining token placements for all the players
     */
    public ArrayList<ArrayList<Integer>> playersTokens;
    public Semaphore tokensLock;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {
        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.playersTokens = new ArrayList<ArrayList<Integer>>();
        this.tokensLock = new Semaphore();
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        if (slotToCard[slot] != null) {
            int card = slotToCard[slot];
            cardToSlot[card] = null;
            slotToCard[slot] = null;
            env.ui.removeCard(slot);
        }
    }

    /**
     * Toggles token placement for a player.
     * Assumes the function call is wrapped in the semaphore's lock. 
     * @param player - the player's id.
     * @param slot   - the slot on which to toggle the token.
     * @return       - whether (env.config.featureSize) token are now placed and the dealer should be called.
     */
    public boolean toggleToken(int player, int slot){
        List<Integer> tokenPlacements;
        tokenPlacements = playersTokens.get(player);
        if (tokenPlacements.contains(slot)) {
            tokenPlacements.remove(tokenPlacements.indexOf(slot));
            removeToken(player, slot);
        }
        else if (tokenPlacements.size() < env.config.featureSize) {
            tokenPlacements.add(slot);
            placeToken(player, slot);
            if (tokenPlacements.size() == env.config.featureSize) return true;
        }
        return false;
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        env.ui.placeToken(player, slot);
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        env.ui.removeToken(player, slot);
        return false;
    }

    /**
     * Filters out the null cards from the table
     * @return an integer array of cards that are on the table
     */
    public Integer[] getCardsOnTable(){
        ArrayList<Integer> cards_ = new ArrayList<Integer>();
        for(Integer slot: slotToCard){
            if (slot == null) continue;
            cards_.add(slot);
        }
        Integer[] cards = new Integer[cards_.size()];
        for(int i = 0; i < cards.length; i++){
            cards[i] = cards_.get(i);
        }
        return cards;
    }
}