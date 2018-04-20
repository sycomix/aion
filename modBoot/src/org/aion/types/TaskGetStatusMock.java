package org.aion.types;

import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.sync.msg.ReqStatus;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class TaskGetStatusMock implements Runnable {

    private final int interval;
    private final static ReqStatus reqStatus = new ReqStatus();
    private final IP2pMgr p2p;
    private final Logger log;
    private final int mockId;
    private final AtomicLong sent;

    public TaskGetStatusMock(final Logger _log, final int mockId, final IP2pMgr _p2p, final int interval, AtomicLong sent) {
        this.p2p = _p2p;
        this.log = _log;
        this.interval = interval;
        this.mockId = mockId;
        this.sent = sent;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void run() {
        while (true) {
            try {
                Set<Integer> ids = new HashSet<>(p2p.getActiveNodes().keySet());

                for (int id : ids) {
                    log.debug("sending reqStatus to {}", id);
                    p2p.send(id, reqStatus);
                    sent.getAndIncrement();
                }
                Thread.sleep(interval);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    break;
                } else {
                    log.error("<sync-gs[{}] exception={}>", mockId, e.toString());
                }
            }
        }
        log.info("<task-get-status[{}] shutdown>", mockId);
    }
}
