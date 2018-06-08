package org.aion.consensus;

import org.aion.base.type.IActor;
import org.aion.mcf.mine.IMineRunner;
import org.aion.zero.impl.blockchain.AionFactory;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.slf4j.Logger;

/**
 * The ConsensusEngine provides high-level calls that abstract away the details of particular
 * consensus algorithm implementations. The ConsensusEngine runs the currently selected consensus
 * algorithm, facilitating swappable consensus algorithms.
 *
 * The ConsensusEngine provides a getActor method that provides a means of creating and retrieving
 * a new actor, where an actor is a performer in the consensus algorithm that generates solutions
 * (eg. for PoW the actor is a miner, for PoS it is a validator).
 *
 * @author nick nadeau
 */
public final class ConsensusEngine {
    private static ConsensusEngine singletonInstance;
    private static boolean isSealed = false;
    private final Engine engine;
    private final boolean isSeed;

    /**
     * The engines or consensus algorithms the ConsensusEngine supports.
     */
    public enum Engine { PoW }

    private ConsensusEngine(CfgAion config) {
        // It may be more practical in the future to simply keep a ref to config.
        this.isSeed = config.getConsensus().isSeed();
        this.engine = Engine.PoW;   //TODO: read in value from config
    }

    /**
     * Attempts to seal config into the ConsensusEngine and returns true on success.
     * This method succeeds on its very first call. Subsequent attempts to call this method will
     * always fail.
     *
     * @param config the initial configurations.
     * @return true only if config was sealed into the ConsensusEngine.
     */
    public static boolean sealConfigurations(CfgAion config) {
        if (isSealed) {
            return false;
        } else {
            singletonInstance = new ConsensusEngine(config);
            isSealed = true;
            return true;
        }
    }

    /**
     * Returns a singleton instance of the ConsensusEngine class only if a configuration has
     * already been sealed, otherwise throws an exception.
     *
     * @return a singleton instance of ConsensusEngine.
     * @throws IllegalStateException if sealConfigurations was not called prior to this call.
     */
    public static ConsensusEngine getInstance() {
        if (!isSealed) { throw new IllegalStateException("No configuration sealed."); }
        return singletonInstance;
    }

    /**
     * Returns a newly created actor appropriate to the engine specified for this ConsensusEngine.
     *
     * @return an actor.
     */
    public IActor getActor() {
        switch (this.engine) {
            case PoW:
                IMineRunner miner = null;

                if (!this.isSeed) {
                    IAionChain ac = AionFactory.create();
                    miner = ac.getBlockMiner();
                }

                return miner;
            default: return null;
        }
    }

    /**
     * Starts actor to begin performing the task associated with it depending on the current engine.
     *
     * @param actor the actor to start.
     * @throws ClassCastException if actor type differs from expectations of current engine.
     */
    public void startActor(IActor actor) {
        switch (this.engine) {
            case PoW:
                startMiner((IMineRunner) actor);
                break;
        }
    }

    /**
     * Stops actor from performing the task associated with it depending on the current engine.
     *
     * @param actor the actor to stop.
     * @throws ClassCastException if actor type differs from expectations of current engine.
     */
    public void stopActor(IActor actor) {
        switch (this.engine) {
            case PoW:
                stopMiner((IMineRunner) actor);
                break;
        }
    }

    /**
     * Shuts actor down appropriately depending on the current engine.
     *
     * @param actor the actor to shut down.
     * @throws ClassCastException if actor type differs from expectations of current engine.
     */
    public void shutdownActor(IActor actor, Logger logger) {
        switch (this.engine) {
            case PoW:
                shutdownMiner((IMineRunner) actor, logger);
                break;
        }
    }

    /**
     * Starts the miner mining with an initial 10 second delay. Does nothing if miner is null.
     *
     * @param miner the miner to start.
     */
    private void startMiner(IMineRunner miner) {
        if (miner != null) {
            miner.delayedStartMining(10);
        }
    }

    /**
     * Stops the miner from mining. Does nothing if miner is null.
     *
     * @param miner the miner to stop.
     */
    private void stopMiner(IMineRunner miner) {
        if (miner != null) {
            miner.stopMining();
        }
    }

    /**
     * Shuts down the miner and logs messages using logger. Does nothing if miner is null.
     *
     * @param miner the miner to shut down.
     * @param logger the logger to log to.
     */
    private void shutdownMiner(IMineRunner miner, Logger logger) {
        if (miner != null) {
            logger.info("Shutting down sealer");
            miner.shutdown();
            logger.info("Shutdown sealer... Done!");
        }
    }

}
