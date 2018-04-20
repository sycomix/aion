package org.aion.types;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ResBlocksBodies;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class ResBlockBodiesHandlerMock extends Handler {

    private final Logger log;
    private final int mockId;
    private final IP2pMgr p2pMgr;
    private final long msgBytesLength;
    private final AtomicLong recv;

    public ResBlockBodiesHandlerMock(final Logger _log, int mockId, final IP2pMgr mgr, final long msgBytesLength, AtomicLong recv) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_BLOCKS_BODIES);
        this.log = _log;
        this.p2pMgr = mgr;
        this.mockId = mockId;
        this.msgBytesLength = msgBytesLength;
        this.recv = recv;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] msgBytes) {
        /*ResBlocksBodies resBlocksBodies = ResBlocksBodies.decode(msgBytes);
        List<byte[]> bodies = resBlocksBodies.getBlocksBodies();
        if(bodies == null || bodies.isEmpty()) {
            log.error("<res-bodies[{}] decoder-error from {}, len: {]>", _displayId, msgBytes.length);
            p2pMgr.errCheck(_nodeIdHashcode, _displayId);
        }*/

        // we know that msgBytes might be ok. just validate the msgBytes.length
        if (msgBytes.length >= msgBytesLength) {
            recv.getAndIncrement();
        } else {
            log.error("<res-bodies[{}] invalid length from {}, len: {]>", _displayId, msgBytes.length);
        }
    }
}
