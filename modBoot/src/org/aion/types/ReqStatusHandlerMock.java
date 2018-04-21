package org.aion.types;

import org.aion.base.util.ByteUtil;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ResStatus;
import org.aion.zero.impl.types.AionBlock;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.Random;

public final class ReqStatusHandlerMock extends Handler {

    private final Logger log;

    private IP2pMgr mgr;
    private final AionBlock genesis;
    private final int mockId;

    private final Random random = new Random(System.currentTimeMillis());

    public ReqStatusHandlerMock(final Logger _log, int mockId, final IP2pMgr mgr, final AionBlock genesis) {
        super(Ver.V0, Ctrl.SYNC, Act.REQ_STATUS);
        this.log = _log;
        this.mgr = mgr;
        this.genesis = genesis;
        this.mockId = mockId;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void receive(int _nodeIdHashcode, String _displayId, byte[] _msg) {
        ResStatus rs = new ResStatus(random.nextInt(1000000), (new BigInteger(String.valueOf(random.nextInt(999999999)))) .toByteArray(), ByteUtil.hexStringToBytes(getRandomHexString(64)), genesis.getHash());
        this.mgr.send(_nodeIdHashcode, rs);
        this.log.debug("<req-status[{}] node={} return-blk={}>", mockId, _nodeIdHashcode, rs.getBestBlockNumber());
    }

    private String getRandomHexString(int numchars){

        StringBuffer sb = new StringBuffer();
        while(sb.length() < numchars){
            sb.append(Integer.toHexString(random.nextInt()));
        }

        return sb.toString().substring(0, numchars);
    }
}
