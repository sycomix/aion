package org.aion.types;

import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class TaskGetBodiesMock implements Runnable {

    private final IP2pMgr p2p;
    private final Logger log;
    private final int mockId;
    private final long interval;
    private final AtomicLong sent;

    private final List<byte[]> requestedBlockHashes;

    public TaskGetBodiesMock(
            final Logger log,
            final int mockId,
            final IP2pMgr p2p,
            final long interval,
            final List<byte[]> requestedBlockHashes,
            AtomicLong sent) {
        this.p2p = p2p;
        this.requestedBlockHashes = requestedBlockHashes;
        this.log = log;
        this.mockId = mockId;
        this.interval = interval;
        this.sent = sent;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void run() {
        while (true) {
            try {
                Set<Integer> ids = new HashSet<>(p2p.getActiveNodes().keySet());

                for (int id : ids) {
                    log.debug("<get-bodies[{}] block count={} node={}>", mockId, requestedBlockHashes.size(), id);
                    p2p.send(id, new ReqBlocksBodies(requestedBlockHashes));
                    sent.getAndIncrement();
                }
                Thread.sleep(interval);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    break;
                } else {
                    log.error("<get-bodies[{}] exception!>", mockId, e);
                }
            }
        }
    }
}
