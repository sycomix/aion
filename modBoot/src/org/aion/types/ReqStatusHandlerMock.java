package org.aion.types;

import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ResStatus;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

public final class ReqStatusHandlerMock extends Handler {

    private final Logger log;

    private IP2pMgr mgr;
    private final AionBlock genesis;
    private final int mockId;

    private final int UPDATE_INTERVAL = 500;
    private volatile ResStatus cache;
    private volatile long cacheTs = 0;

    public ReqStatusHandlerMock(final Logger _log, int mockId, final IP2pMgr mgr, final AionBlock genesis) {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_STATUS);
        this.log = _log;
        this.mgr = mgr;
        this.genesis = genesis;
        this.mockId = mockId;
        this.cache = new ResStatus(0, genesis.getDifficultyBI().toByteArray(), genesis.getHash(), genesis.getHash());
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void receive(int _nodeIdHashcode, String _displayId, byte[] _msg) {

        this.mgr.send(_nodeIdHashcode, cache);
        this.log.debug("<req-status[{}] node={} return-blk={}>", mockId, _nodeIdHashcode, cache.getBestBlockNumber());
    }
}
