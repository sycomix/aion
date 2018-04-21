package org.aion.types;

import org.aion.base.util.ByteUtil;
import org.aion.mcf.types.BlockIdentifier;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.ReqBlocksBodies;
import org.aion.zero.impl.sync.msg.ReqBlocksHeaders;
import org.aion.zero.impl.sync.msg.ResBlocksHeaders;
import org.aion.zero.types.A0BlockHeader;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


public final class ResBlockHeadersHandlerMock extends Handler {

    private final Logger log;
    private final IP2pMgr p2p;
    private final int mockId;

    private final AtomicLong sent;
    private final AtomicLong recv;
    private final AtomicLong recv_time;

    public ResBlockHeadersHandlerMock(final Logger log, int mockId, final IP2pMgr p2p, AtomicLong sent, AtomicLong recv, AtomicLong recv_time) {
        super(Ver.V0, Ctrl.SYNC, Act.RES_BLOCKS_HEADERS);
        this.log = log;
        this.p2p = p2p;
        this.mockId = mockId;

        this.sent = sent;
        this.recv = recv;
        this.recv_time = recv_time;
    }

    @Override
    public void receive(int _nodeIdHashcode, String _displayId, final byte[] _msgBytes) {
        ResBlocksHeaders resHeaders = ResBlocksHeaders.decode(_msgBytes);
        if(resHeaders != null) {
            List<A0BlockHeader> headers = resHeaders.getHeaders();
            if(headers != null && headers.size() > 0){
                this.log.debug("<res-headers[{}] from-number={} size={} node={}>", mockId, headers.get(0).getNumber(), headers.size(), _displayId);
                recv.getAndIncrement();
                recv_time.set(Instant.now().getEpochSecond());

                List<byte[]> requestedBlockHashes = new ArrayList<>();
                for (A0BlockHeader bh : headers) {
                    requestedBlockHashes.add(bh.getHash());
                }
                // now send for block bodies,
                log.debug("<get-bodies[{}] block count={} node={}>", mockId, requestedBlockHashes.size(), _displayId);
                p2p.send(_nodeIdHashcode, new ReqBlocksBodies(requestedBlockHashes));
                sent.getAndIncrement();

            } else {
                log.error("<res-headers[{}] empty-headers node={} >", mockId, _displayId);
            }
        } else {
            log.error("<res-headers[{}] decode-error msg-bytes={} node={}>", mockId, _msgBytes.length, _displayId);
        }
    }
}