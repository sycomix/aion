package org.aion.types;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ResBlocksBodies;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class ResBlockBodiesHandlerMock extends Handler {

    private final Logger log;
    private final int mockId;
    private final IP2pMgr p2p;
    private final AtomicLong recv;
    private final AtomicLong recv_time;

    public ResBlockBodiesHandlerMock(Logger log, int mockId, IP2pMgr mgr, AtomicLong recv, AtomicLong recv_time) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_BLOCKS_BODIES);
        this.log = log;
        this.p2p = mgr;
        this.mockId = mockId;
        this.recv = recv;
        this.recv_time = recv_time;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] msgBytes) {
        // don't care waht we got back for now.
        // TODO: validate what we get back is good
        recv.getAndIncrement();
        recv_time.set(Instant.now().getEpochSecond());
        this.log.debug("<res-bodies[{}] node={}>", mockId, _nodeIdHashcode);
    }
}
