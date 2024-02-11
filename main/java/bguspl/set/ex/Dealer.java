package bguspl.set.ex;

import bguspl.set.Env;

import java.util.concurrent.locks.ReentrantLock;
import java.util.*;
import java.util.logging.Level;
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
    protected final Player[] players;

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
    //    protected boolean tableReady;
    protected int[] dealerSetQueue;
    protected Thread[] playerThread;
    protected Integer checkSetByPlayerId = -1;
    protected static final ReentrantLock lock = new ReentrantLock(true);
    private long curtime = 0;
    private long reset = System.currentTimeMillis();
    protected long[] penaltyArray;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        dealerSetQueue = new int[env.config.featureSize];
        penaltyArray = new long[env.config.players];
        for (int i = 0; i < penaltyArray.length; i++)
            penaltyArray[i] = -1;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        playerThread = new Thread[players.length];
        for (Integer i = 0; i < playerThread.length; i++)
            playerThread[i] = new Thread(players[i], env.config.playerNames[i]);
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        for (Integer i = 0; i < playerThread.length; i++)
            playerThread[i].start();
        while (!shouldFinish()) {
            placeCardsOnTable();
            table.tableReady = true;
            timerLoop();
            if (!terminate) {
                updateTimerDisplay(false);
                table.tableReady = false;
                List<Integer> cardsOnTable = new LinkedList<>();
                for (int i = 0; i < env.config.tableSize; i++)
                    if (table.slotToCard[i] != null) {
                        cardsOnTable.add(table.slotToCard[i]);
                    }//checks if there is a set on the table
                if ((env.util.findSets(cardsOnTable, 1).size() == 0) && (env.util.findSets(deck, 1).size() == 0)) {
                    List<Integer> allCards = new LinkedList<>();
                    for (Integer card : deck) {
                        allCards.add(card);
                    }
                    for (Integer card : cardsOnTable) {
                        allCards.add(card);
                    }
                    if (env.util.findSets(allCards, 1).size() == 0) {//checks if there is a set in the available cards
                        terminate();
                    }
                } else
                    removeAllCardsFromTable();
            }
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");

    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        if (env.config.turnTimeoutMillis < 0 || env.config.turnTimeoutMillis == 0) {//bonus modes:0 and -1
            List<Integer> cardsOnTable = new LinkedList<>();
            for (int i = 0; i < env.config.tableSize; i++)//adding all the cards that are on the table to the list
                cardsOnTable.add(table.slotToCard[i]);
            while (env.util.findSets(cardsOnTable, 1).size() == 0 && !shouldFinish()) {//if there is no sets on the table. reshuffles
                removeAllCardsFromTable();
                placeCardsOnTable();
                cardsOnTable = new LinkedList<>();
                for (int i = 0; i < env.config.tableSize; i++)
                    if (table.slotToCard[i] != null)
                        cardsOnTable.add(table.slotToCard[i]);
            }
            while (!shouldFinish() && env.util.findSets(cardsOnTable, 1).size() > 0) {//this loop and for the first run of the loop only
                sleepUntilWokenOrTimeout();
                updateTimerDisplay(false);
                placeCardsOnTable();
                cardsOnTable = new LinkedList<>();
                for (int i = 0; i < env.config.tableSize; i++) {
                    if (table.slotToCard[i] != null)
                        cardsOnTable.add(table.slotToCard[i]);
                }
                table.tableReady = true;
                while (env.util.findSets(cardsOnTable, 1).size() == 0 && !shouldFinish()) {//removing all cards from the table until we have a set
                    table.tableReady = false;
                    removeAllCardsFromTable();
                    placeCardsOnTable();
                    cardsOnTable = new LinkedList<>();
                    for (int i = 0; i < env.config.tableSize; i++)
                        cardsOnTable.add(table.slotToCard[i]);
                }
            }
        } else {//regular mode
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            while (!shouldFinish() && System.currentTimeMillis() < reshuffleTime) {
                table.tableReady = true;
                sleepUntilWokenOrTimeout();
                updateTimerDisplay(false);
                placeCardsOnTable();
            }
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        List<Integer> cardsOnTable = new LinkedList<>();
        for (int i = 0; i < env.config.tableSize; i++) {
            if (table.slotToCard[i] != null) {
                cardsOnTable.add(table.slotToCard[i]);
            }
        }
        return terminate || (env.util.findSets(deck, 1).size() == 0) && (env.util.findSets(cardsOnTable, 1).size() == 0);
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        Integer slot;
        for (int i = 0; i < dealerSetQueue.length; i++) {
            try {
                slot = table.cardToSlot[dealerSetQueue[i]];
                for (int j = 0; j < players.length; j++) {
                    if (table.slotstotokens[slot][j] != null) {
                        for (int t = 0; t < players[j].setQueue.length; t++) {//removing the card from all players if it is in their setqueue
                            if (players[j].setQueue[t] == table.slotToCard[slot])
                                players[j].setQueue[t] = -1;
                        }
                        players[j].availableToken++;
                        table.removeToken(j, slot);
                    }
                }
                table.removeCard(slot);
            } catch (NullPointerException ignored) {//using optimistic try and fail, if by some unexplainable reason a null or -1 got into the playersetqueue, handle this by granting the token back to the player and ignore the "placing" of the token
                for (int x = i; x < players[checkSetByPlayerId].setQueue.length; x++) {
                    players[checkSetByPlayerId].setQueue[x] = -1;
                    players[checkSetByPlayerId].availableToken++;
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                players[checkSetByPlayerId].availableToken++;
            }
        }
        checkSetByPlayerId = -1;
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        int card;
        List<Integer> random = new LinkedList<>();
        for (int i = 0; i < table.slotToCard.length; i++) {
            random.add(i);
        }
        Collections.shuffle(random);
        if (!deck.isEmpty()) {
            if (table.countCards() < table.slotToCard.length)//if there is an empty spot on the table
            {
                Collections.shuffle(deck);//shuffle the deck
                for (Integer i : random) {
                    if (table.slotToCard[i] == null && !deck.isEmpty()) {//place the cards on the table
                        card = deck.remove(0);
                        table.placeCard(card, i);
                        env.ui.placeCard(card, i);
                    }
                }
            }
        }
    }


    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            Thread.currentThread().sleep(1);
        } catch (InterruptedException e) {
        }
        if (checkSetByPlayerId >= 0) { //if a player changed this flag that means there is a set to check
            if (env.util.testSet(dealerSetQueue)) {
                table.tableReady = false;
                players[checkSetByPlayerId].point();
                penaltyArray[checkSetByPlayerId] = System.currentTimeMillis() + (env.config.pointFreezeMillis);
                removeCardsFromTable();
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                reset = System.currentTimeMillis();//for the elapsed time bonus
            } else {
                players[checkSetByPlayerId].penalty();
                penaltyArray[checkSetByPlayerId] = System.currentTimeMillis() + (env.config.penaltyFreezeMillis);
            }
            checkSetByPlayerId = -1;
        }
        curtime = System.currentTimeMillis() - reset;
        updateTimerDisplay(false);

    }


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (!reset) {
            // Start the countdown timer
            if (env.config.turnTimeoutMillis > 0) {
                if (reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis) {
                    env.ui.setCountdown(Math.max(0, reshuffleTime - System.currentTimeMillis()), false);
                } else {
                    env.ui.setCountdown(Math.max(0, reshuffleTime - System.currentTimeMillis()), true);
                }
                for (int i = 0; i < penaltyArray.length; i++) {
                    if (penaltyArray[i] >= 0) {

                        if (env.config.penaltyFreezeMillis > 0) {
                            env.ui.setFreeze(i, penaltyArray[i] - System.currentTimeMillis() );
                            if (penaltyArray[i] < System.currentTimeMillis())
                                penaltyArray[i] = -1;
                        }
                    }
                }
            } else {
                if (env.config.turnTimeoutMillis == 0)//for the bonus mode part
                    env.ui.setCountdown(curtime, false);
                for (int i = 0; i < penaltyArray.length; i++) {
                    if (penaltyArray[i] >= 0) {
                        if (env.config.penaltyFreezeMillis > 0) {
                            env.ui.setFreeze(i, penaltyArray[i] - System.currentTimeMillis());
                            if (penaltyArray[i] < System.currentTimeMillis())
                                penaltyArray[i] = -1;
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    protected void removeAllCardsFromTable() {
        List<Integer> random = new LinkedList<>();
        for (int i = 0; i < table.slotToCard.length; i++)
            random.add(i);
        Collections.shuffle(random);
        for (Integer i : random) {
            for (int j = 0; j < players.length; j++) {
                if (table.slotstotokens[i][j] != null) {
                    table.removeToken(j, i);
                    players[j].availableToken++;
                    for (int x = 0; x < players[j].setQueue.length; x++) {
                        if (players[j].setQueue[x] == table.slotToCard[i])//removing the card from the setqueue so that it wont stay there for later checks
                            players[j].setQueue[x] = -1;
                    }
                }
            }
            deck.add(table.slotToCard[i]);
            table.removeCard(i);
            env.ui.removeCard(i);
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max = -1, counter = 0;
        for (int i = 0; i < players.length; i++) {
            for (int j = 0; j < env.config.tableSize; j++) {
                table.removeToken(i, j);
            }
            if (players[i].score() >= max) {
                if (players[i].score() == max) {
                    counter++;
                } else {
                    counter = 1;
                    max = players[i].score();
                }
            }
        }
        int[] ans = new int[counter];
        counter = 0;
        for (int i = 0; i < players.length; i++) {
            if (players[i].score() == max) {
                ans[counter] = players[i].id;
                counter++;
            }
        }
        env.ui.announceWinner(ans);
        for (int i = playerThread.length - 1; i >= 0; i--) {//ending all players threads gracefully and in reverse order
            players[i].terminate();
            playerThread[i].interrupt();
            env.logger.log(Level.INFO, "Thread " + playerThread[i].getName() + " terminated.");
        }
        terminate();
    }

    public boolean getTerminate() {
        return terminate;
    }
}
