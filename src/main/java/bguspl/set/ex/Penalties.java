package bguspl.set.ex;

/**
 * This class conatins current penalties for illegal sets for all players
 * 
 * @inv penalties[i] = true iff an ilegal set was handed to the dealer & player hadn't penalized itself yet
 */
public class Penalties {
    public boolean[] penalties;

    /**
     * Constructor
     * 
     * @param playersLength
     */
    public Penalties(int playersLength){
        this.penalties = new boolean[playersLength];
    }
    /**
     * Sets a penalty to a player
     * 
     * @param playerId
     
     * @post - penalties[playerId] = true
     */
    public void setPenalty(int playerId){
        penalties[playerId] = true;
    }

    /**
     * Removes a penalty from a player
     * 
     * @param playerId
     
     * @post - penalties[playerId] = false
     */
    public void removePenalty(int playerId){
        penalties[playerId] = false;
    }

    /**
     * Returns whether a player deserves a heavy penalty
     * 
     * @param playerId
     * @return true iff penalties[playerId] == true
     */
    public boolean getPenalty(int playerId){
        return penalties[playerId];
    }
}
