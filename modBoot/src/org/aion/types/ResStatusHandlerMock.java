package org.aion.types;

import org.aion.base.util.ByteUtil;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ResStatus;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

public final class ResStatusHandlerMock extends Handler {

    private final Logger log;
    private final int mockId;
    private final AtomicLong recv;

    public ResStatusHandlerMock(final Logger _log, int mockId, AtomicLong recv) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_STATUS);
        this.log = _log;
        this.mockId = mockId;
        this.recv = recv;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        if (_msgBytes == null || _msgBytes.length == 0) {
            log.error("<res-status[{}] empty msg bytes received from {}>", mockId, _nodeIdHashcode);
            return;
        }

        ResStatus rs = ResStatus.decode(_msgBytes);

        if (rs == null) {
            log.error("<res-status[{}] decode-error from {} len: {}>", mockId, _nodeIdHashcode, _msgBytes.length);
            if (log.isTraceEnabled()) {
                log.trace("res-status[{}] decode-error dump: {}", mockId, ByteUtil.toHexString(_msgBytes));
            }
        }

        // success?
        log.debug("<res-status node={} best-blk={}>", _nodeIdHashcode, rs.getBestBlockNumber());
        recv.getAndIncrement();
    }
}
