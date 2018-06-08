package org.aion.consensus;

import org.aion.base.type.ISolution;

/**
 * The ConsensusEngine provides high-level calls that abstract away the details of particular
 * consensus algorithm implementations. The ConsensusEngine runs the currently selected consensus
 * algorithm, facilitating swappable consensus algorithms.
 *
 * @author nick nadeau
 */
public final class ConsensusEngine {
    private static ConsensusEngine singletonInstance;
    private static boolean isSealed = false;
    private final Engine engine;

    /**
     * The engines or consensus algorithms the ConsensusEngine supports.
     */
    public enum Engine { PoW }

    private ConsensusEngine(Engine engine) {
        this.engine = engine;
    }

    /**
     * Attempts to seal the specified consensus algorithm implementation (engine) into this
     * ConsensusEngine.
     *
     * Returns true if engine was successfully sealed and false otherwise. Note that this method
     * returns false only if an engine was already sealed -- this method can only be called once.
     * Once an engine is sealed it cannot be altered during the program's duration.
     *
     * @param engine the engine to attempt to seal.
     * @return true only if no engine has been sealed yet and engine was successfully sealed.
     */
    public static boolean sealEngine(Engine engine) {
        if (isSealed) {
            return false;
        } else {
            singletonInstance = new ConsensusEngine(engine);
            return true;
        }
    }

    /**
     * Returns a singleton instance of the ConsensusEngine class only if a specific engine has
     * already been sealed, otherwise throws an exception if no engine has already been sealed.
     *
     * @return a singleton instance of ConsensusEngine.
     */
    public static ConsensusEngine getInstance() {
        if (!isSealed) { throw new IllegalStateException("No engine sealed."); }
        return singletonInstance;
    }

    /**
     * Uses the currently selected consensus algorithm to produce a solution.
     *
     * @return a solution.
     */
    public static ISolution produceSolution() {
        //TODO
        return null;
    }

    /**
     * Uses the currently selected consensus algorithm to verify a solution.
     *
     * @param solution the solution to verify.
     */
    public static void verifySolution(ISolution solution) {
        //TODO
    }

}
