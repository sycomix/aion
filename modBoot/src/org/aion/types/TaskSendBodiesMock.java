package org.aion.types;

import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.p2p.*;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.impl.sync.msg.ResBlocksBodies;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;


/**
 * @author chris handler for request block bodies broadcasted from network
 */
public final class TaskSendBodiesMock implements Runnable {

    private final Logger log;
    private final IP2pMgr p2p;
    private final int interval;
    private final int mockId;
    private final ResBlocksBodies bodies;
    AtomicLong pushed;

    public TaskSendBodiesMock(final Logger _log, int mockId, final IP2pMgr mgr, final int interval, ResBlocksBodies bodies, AtomicLong pushed) {
        this.log = _log;
        this.p2p = mgr;
        this.interval = interval;
        this.mockId = mockId;
        this.bodies = bodies;
        this.pushed = pushed;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void run() {
        while (true) {
            try {
                Set<Integer> ids = new HashSet<>(p2p.getActiveNodes().keySet());

                for (int id : ids) {
                    p2p.send(id, bodies);
                    pushed.getAndIncrement();
                    log.debug("<send-bodies[{}] to {}", mockId, id);
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
        log.info("<task-send-bodies[{}] shutdown>", mockId);




    }
}


