/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.zero.impl.sync;

import static org.aion.zero.impl.sync.PeerState.State.HEADERS_REQUESTED;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.aion.p2p.INode;
import org.slf4j.Logger;

/** @author Alexandra Roatis */
public class PeerStateMgr {

    private static final long expectedTimeDiff = 5000L;

    private final Map<Integer, PeerState> peerStates;

    private final Logger log;

    PeerStateMgr(Map<Integer, PeerState> peerStates, Logger log) {
        this.peerStates = peerStates;
        this.log = log;
    }

    private List<INode> filteredNodes;
    private long timeOfLastRefresh;

    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** Checks that the peer's total difficulty is higher than the local chain. */
    private boolean isHigherTotalDifficulty(final INode n, final BigInteger selfTd) {
        return n.getTotalDifficulty() != null && n.getTotalDifficulty().compareTo(selfTd) >= 0;
    }

    /** Checks that the required time has passed since the last request. */
    private boolean isTimelyRequest(final long now, final INode n) {
        return (now - expectedTimeDiff)
                        > peerStates
                                .computeIfAbsent(n.getIdHash(), k -> new PeerState())
                                .getLastHeaderRequest()
                && peerStates.get(n.getIdHash()).getState() != HEADERS_REQUESTED;
    }

    public INode getRandomNodeForHeaderRequest(
            final Collection<INode> nodes, final long now, final Random random, BigInteger td) {
        lock.writeLock().lock();

        try {
            // use an old filtered node
            if (!filteredNodes.isEmpty() && timeOfLastRefresh - now < expectedTimeDiff) {
                return filteredNodes.remove(random.nextInt(filteredNodes.size()));
            }
            // refresh
            timeOfLastRefresh = System.currentTimeMillis();

            // filter nodes by total difficulty
            filteredNodes =
                    nodes.stream()
                            .filter(n -> isHigherTotalDifficulty(n, td) && isTimelyRequest(now, n))
                            .collect(Collectors.toList());

            if (filteredNodes.isEmpty()) {
                return null;
            }

            // pick one random node
            return filteredNodes.get(random.nextInt(filteredNodes.size()));
        } finally {
            lock.writeLock().unlock();
        }
    }
}
