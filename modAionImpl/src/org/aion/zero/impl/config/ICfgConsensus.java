package org.aion.zero.impl.config;

/**
 * An interface for all specific consensus related Cfg classes.
 *
 * @author nick nadeau
 */
public interface ICfgConsensus {

    /**
     * Returns true only if the actor is active for this session. That is, if the actor is actively
     * producing solutions. For example, for the PoW algorithm this method will tell you whether
     * mining is turned on or off.
     *
     * @return true only if the actor is active.
     */
    boolean isActorActive();

    /**
     * Returns the account address of the actor.
     *
     * @return the account address of the actor.
     */
    String getActorAddress();

    /**
     * Returns the number of cpu threads to use.
     *
     * @return the number of cpu threads to use.
     */
    byte getNumCpuThreads();

    /**
     * Returns any extra data provided in the config file for the consensus implementation.
     *
     * @return any extra data.
     */
    String getExtraData();

    /**
     * Returns the energy strategy to use.
     *
     * @return the energy strategy.
     */
    CfgEnergyStrategy getEnergyStrategy();

    /**
     * Returns true only if this node is seed.
     *
     * @return true only if seed.
     */
    boolean isSeed();

    /**
     * Sets the actor's active status to isActive.
     *
     * @param isActive the actor's active status.
     */
    void setIsActorActive(boolean isActive);

}
