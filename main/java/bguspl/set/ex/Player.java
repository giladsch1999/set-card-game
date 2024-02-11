package bguspl.set.ex;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;

import bguspl.set.Env;


/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
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
    protected volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    protected Integer availableToken;
    protected ArrayBlockingQueue<Integer> pressQueue;
    protected int[] setQueue;
    protected Dealer dealer;
    protected Integer sleep;


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
        this.dealer = dealer;
        availableToken = env.config.featureSize;
        pressQueue = new ArrayBlockingQueue<>(env.config.featureSize);
        setQueue = new int[env.config.featureSize];
        for (int i = 0; i < setQueue.length; i++)
            setQueue[i] = -1;
        this.score = 0;
        sleep = 0;


    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted() && !terminate) {
            playerThread = Thread.currentThread();
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
            if (!human) createArtificialIntelligence();
            while (!terminate) {
                if (sleep != 0) {//checks if the player got a penalty or point and thus need to sleep for a certain amount of time (the dealer changes this field when he grants a point/penalty for a player)
                    try {
                        if (sleep == env.config.pointFreezeMillis) {
                            env.ui.setFreeze(id, env.config.pointFreezeMillis);
                        } else {
                            env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
                        }
                        playerThread.sleep(sleep);
                    } catch (InterruptedException e) {
                    }
                    env.ui.setFreeze(id, 0);
                    sleep = 0;
                }
                Integer slot;
                if (!pressQueue.isEmpty() && table.tableReady) {
                    slot = pressQueue.remove();//the slot that came out of the keypressed method
                    if (table.slotstotokens[slot][id] == null && availableToken > 0 && table.tableReady) {
                        table.placeToken(id, slot);
                        availableToken--;
                        for (int j = 0; j < env.config.featureSize; j++) {
                            try {
                                if (setQueue[j] == -1) {//checking if the spot in the set queue is empty, if it is put the card inside it maybe needs this && table.slotToCard[slot]!=null
                                    setQueue[j] = table.slotToCard[slot];
                                    break;
                                }
                            } catch (NullPointerException exp) {
                               //if by some reason we got null in the queue handled this by ignoring this and giving the token back to the player (optimistic try and fail)
                                availableToken++;
                                break;
                            }
                        }
                        if (availableToken == 0) {
                            dealer.lock.lock();//locking the fair lock
                            if (availableToken == 0) {//if by some reason it changed in this time
                                for (int i = 0; i < setQueue.length; i++) { //giving the dealer my set to check
                                    dealer.dealerSetQueue[i] = setQueue[i];
                                }
                                dealer.checkSetByPlayerId = id;//giving the dealer my id
                                while (dealer.checkSetByPlayerId == id && !terminate) {
                                    try {
                                        if(!terminate)
                                        Thread.currentThread().sleep(1);


                                    } catch (InterruptedException ignore) {}
                                }
                            }
                            dealer.lock.unlock();
                        }
                    } else {
                        if (table.slotstotokens[slot][id] != null) {
                            table.removeToken(id, slot);
                            availableToken++;
                            for (int i = 0; i < env.config.featureSize; i++) {//checking which is the right token to remove
                                if (setQueue[i] == table.slotToCard[slot])
                                    setQueue[i] = -1;
                            }
                        }
                    }
                }
            }
            if (!human) try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }

    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                if (table.tableReady) {
                    int slot = (int) (Math.random() * env.config.tableSize);
                    keyPressed(slot);
                }
            }
        });
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        if (!human) {
            aiThread.interrupt();
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (sleep == 0 && table.tableReady == true && pressQueue.size()<3) {
            try {
                pressQueue.put(slot);
            } catch (InterruptedException ignored) {
            }
        }

        /**
         * Award a point to a player and perform other related actions.
         *
         * @post - the player's score is increased by 1.
         * @post - the player's score is updated in the ui.
         */
    }

    public void point() {
        sleep = (int) env.config.pointFreezeMillis;
        score++;
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        sleep = (int) env.config.penaltyFreezeMillis;
    }

    public int score() {
        return score;
    }
}
