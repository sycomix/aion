package org.aion.types;

import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.sync.msg.ReqBlocksHeaders;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;


public final class TaskGetHeadersMock implements Runnable {

    private final IP2pMgr p2p;
    private final int mockId;
    private final Logger log;
    private final AtomicLong sent;
    private final int latestTargetBlock;
    private final int interval;

    private final Random random = new Random(System.currentTimeMillis());

    public TaskGetHeadersMock(final Logger log, final int mockId, final IP2pMgr p2p, int interval, AtomicLong sent, int latestTargetBlock) {
        this.p2p = p2p;
        this.mockId = mockId;
        this.log = log;
        this.sent = sent;
        this.latestTargetBlock = latestTargetBlock;
        this.interval = interval;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Set<Integer> ids = new HashSet<>(p2p.getActiveNodes().keySet());

                for (int id : ids) {
                    ReqBlocksHeaders rbh = new ReqBlocksHeaders(random.nextInt(latestTargetBlock), 32);
                    this.p2p.send(id, rbh);

                    sent.getAndIncrement();
                    log.debug("<req-header[{}] to {}", mockId, id);
                }
                Thread.sleep(interval);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    break;
                } else {
                    log.error("<req-header[{}] exception={}>", mockId, e.toString());
                }
            }
        }
        log.info("<req-header[{}] shutdown>", mockId);
    }
}
