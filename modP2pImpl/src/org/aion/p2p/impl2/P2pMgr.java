/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https:www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl2;

import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.TaskRequestActiveNodes;
import org.aion.p2p.impl.TaskUPnPManager;
import org.aion.p2p.*;
import org.aion.p2p.impl.comm.Act;
import org.aion.p2p.impl.zero.msg.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author chris
 */
public final class P2pMgr implements IP2pMgr {

    private final static int PERIOD_SHOW_STATUS = 10000;
    private final static int PERIOD_REQUEST_ACTIVE_NODES = 1000;
    private final static int PERIOD_CONNECT_OUTBOUND = 1000;
    private final static int PERIOD_CLEAR = 10000;
    private final static int PERIOD_UPNP_PORT_MAPPING = 3600000;

    private final static int TIMEOUT_OUTBOUND_CONNECT = 3000;
    private final static int TIMEOUT_OUTBOUND_NODES = 10000;
    private final static int TIMEOUT_INBOUND_NODES = 10000;
    private final static int TIMEOUT_MSG_READ = 10000;

    private final static int MAX_CHANNEL_OUT_QUEUE = 8;

    private final int maxTempNodes;
    private final int maxActiveNodes;
    private final int errTolerance;
    private final static int txBroadCastRoute = (Ctrl.SYNC << 8) + 6; // ((Ver.V0 << 16) + (Ctrl.SYNC << 8) + 6);

    private final boolean syncSeedsOnly;
    private final boolean showStatus;
    private final boolean showLog;
    private final int selfNetId;
    private final String selfRevision;
    private final byte[] selfNodeId;
    private final int selfNodeIdHash;
    private final String selfShortId;
    private final byte[] selfIp;
    private final int selfPort;
    private final boolean upnpEnable;

    private final Map<Integer, List<Handler>> handlers = new ConcurrentHashMap<>();
    private final Set<Short> selfVersions = new HashSet<>();

    private final Set<String> seedIps = new HashSet<>();
    private final BlockingQueue<Node> tempNodes = new LinkedBlockingQueue<>();
    private final Map<Integer, Node> outboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> inboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> activeNodes = new ConcurrentHashMap<>();

    private AtomicBoolean start = new AtomicBoolean(true);
    private ServerSocketChannel tcpServer;
    private Selector selector;
    private final ThreadPoolExecutor processWorkers;
    private final ThreadPoolExecutor writeWorkers;
    private Thread tInbound, tShowStatus, tConnectPeers, tGetActivePeers, tClear;
    private ScheduledThreadPoolExecutor scheduledWorkers;

    private final class TaskInbound implements Runnable {
        @Override
        public void run() {
            while (start.get()) {

                int num;
                try {
                    num = selector.select(10);
                } catch (IOException e) {
                    if (showLog)
                        System.out.println("<p2p inbound-select-io-exception>");
                    e.printStackTrace();
                    continue;
                }

                if (num == 0)
                    continue;

                Iterator<SelectionKey> keys = null;
                try{
                    keys = selector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey sk = null;
                        try{
                            sk = keys.next();
                            if (!sk.isValid())
                                continue;
                            if (sk.isAcceptable())
                                accept();
                            if (sk.isReadable())
                                read(sk);
                        } catch (IOException e){
                            closeSocket((SocketChannel) sk.channel(), "inbound-io-exception");
                            e.printStackTrace();
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                    }

                } catch (Exception ex){
                    if(showLog)
                        System.out.println("<p2p-pi exception=" + ex.getMessage() + ">");
                } finally {
                    if(keys != null)
                        keys.remove();
                }
            }
            if (showLog)
                System.out.println("<p2p-pi shutdown>");
        }
    }

    private final class TaskDiscardPolicy implements RejectedExecutionHandler {
        TaskDiscardPolicy() {}
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if(showLog)
                System.out.println("<p2p workers-queue-full>");
        }
    }

    private final class TaskStatus implements Runnable {
        @Override
        public void run() {
            Thread.currentThread().setName("p2p-ts");
            while(P2pMgr.this.start.get()){
                try{
                    StringBuilder sb = new StringBuilder();
                    sb.append("\n");
                    sb.append(String.format("================================================================== p2p-status-%6s ==================================================================\n", selfShortId));
                    sb.append(String.format("temp[%3d] inbound[%3d] outbound[%3d] active[%3d]                            s - seed node, td - total difficulty, # - block number, bv - binary version\n", tempNodes.size(), 0, 0, activeNodes.size()));
                    List<Node> sorted = new ArrayList<>(activeNodes.values());
                    if (sorted.size() > 0) {
                        sb.append("\n          s");
                        sb.append("               td");
                        sb.append("          #");
                        sb.append("                                                             hash");
                        sb.append("              ip");
                        sb.append("  port");
                        sb.append("     conn");
                        sb.append("              bv\n");
                        sb.append("-------------------------------------------------------------------------------------------------------------------------------------------------------\n");
                        sorted.sort((n1, n2) -> {
                            int tdCompare = n2.getTotalDifficulty().compareTo(n1.getTotalDifficulty());
                            if (tdCompare == 0) {
                                Long n2Bn = n2.getBestBlockNumber();
                                Long n1Bn = n1.getBestBlockNumber();
                                return n2Bn.compareTo(n1Bn);
                            } else
                                return tdCompare;
                        });
                        for (Node n : sorted) {
                            try {
                                sb.append(
                                    String.format("id:%6s %c %16s %10d %64s %15s %5d %8s %15s\n",
                                        n.getIdShort(),
                                        n.getIfFromBootList() ? 'y' : ' ', n.getTotalDifficulty().toString(10),
                                        n.getBestBlockNumber(),
                                        n.getBestBlockHash() == null ? "" : Utility.bytesToHex(n.getBestBlockHash()), n.getIpStr(),
                                        n.getPort(),
                                        n.getConnection(),
                                        n.getBinaryVersion()
                                    )
                                );
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                    sb.append("\n");
                    System.out.println(sb.toString());
                    Thread.sleep(PERIOD_SHOW_STATUS);
                } catch (InterruptedException e){
                    if(showLog)
                        System.out.println("<p2p-ts shutdown>");
                    return;
                } catch (Exception e){
                    if(showLog)
                        e.printStackTrace();
                }
            }
        }
    }

    private final class TaskGetActiveNodes implements  Runnable {
        @Override
        public void run() {
            while(start.get()){
                INode node = getRandom();
                if (node != null)
                    send(node.getIdHash(), node.getIdShort(), new ReqActiveNodes());
                try {
                    Thread.sleep(PERIOD_REQUEST_ACTIVE_NODES);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private final class TaskConnect implements Runnable {

        @Override
        public void run() {
            Thread.currentThread().setName("p2p-connect");
            while (start.get()) {
                try {
                    Thread.sleep(PERIOD_CONNECT_OUTBOUND);
                } catch (InterruptedException e) {
                    if (showLog)
                        System.out.println("<p2p-tcp interrupted>");
                }

                if (activeNodes.size() >= maxActiveNodes) {
                    if (showLog)
                        System.out.println("<p2p-tcp-connect-peer pass max-active-nodes>");
                    return;
                }

                Node node;
                try {
                    node = tempNodes.take();
                    if (node.getIfFromBootList())
                        tempNodes.offer(node);
                } catch (InterruptedException e) {
                    if (showLog)
                        System.out.println("<p2p outbound-connect-io-exception>");
                    return;
                }
                int nodeIdHash = node.getIdHash();

                if (outboundNodes.get(nodeIdHash) == null && activeNodes.get(nodeIdHash) == null) {
                    int _port = node.getPort();
                    try {
                        SocketChannel channel = SocketChannel.open();
                        if (showLog)
                            System.out.println("<p2p try-connect-" + node.getIpStr() + ">");
                        channel.socket().connect(
                                new InetSocketAddress(node.getIpStr(), _port),
                                TIMEOUT_OUTBOUND_CONNECT
                        );
                        configChannel(channel);

                        if (channel.finishConnect()) {
                            SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);
                            ChannelBuffer rb = new ChannelBuffer(P2pMgr.this.showLog, P2pMgr.MAX_CHANNEL_OUT_QUEUE);
                            rb.displayId = node.getIdShort();
                            rb.nodeIdHash = nodeIdHash;
                            sk.attach(rb);

                            node.refreshTimestamp();
                            node.setChannel(channel);
                            if(outboundNodes.putIfAbsent(nodeIdHash, node) == null){
                                writeWorkers.submit(
                                    new TaskWrite(
                                        writeWorkers,
                                        showLog,
                                        node.getIdShort(),
                                        channel,
                                        new ReqHandshake1(
                                            selfNodeId,
                                            selfNetId,
                                            selfIp,
                                            selfPort,
                                            selfRevision.getBytes(),
                                            new ArrayList<>(selfVersions)
                                        ),
                                        rb
                                    )
                                );
                                if (showLog)
                                    System.out.println("<p2p success-add-outbound node=" + node.getIdShort() + " ip=" + node.getIpStr() + ">");
                            } else {
                                channel.close();
                                if (showLog)
                                    System.out.println("<p2p fail-add-outbound error=exist addr=" + node.getIpStr() + ":" + _port);
                            }
                        } else {
                            channel.close();
                        }
                    } catch (IOException e) {
                        if (showLog)
                            System.out.println("<p2p action=connect-outbound addr=" + node.getIpStr() + ":" + _port
                                    + " result=failed>");
                    }
                }
            }
        }
    }

    private final class TaskClear implements Runnable {
        @Override
        public void run() {
            Thread.currentThread().setName("p2p-clr");
            while (start.get()) {
                try {
                    Thread.sleep(PERIOD_CLEAR);
                    timeout(inboundNodes, "inbound", TIMEOUT_INBOUND_NODES);
                    Thread.sleep(PERIOD_CLEAR);
                    timeout(inboundNodes, "outbound", TIMEOUT_OUTBOUND_NODES);
                    Thread.sleep(PERIOD_CLEAR);
                    timeoutActive();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * @param _nodeId         byte[36]
     * @param _ip             String
     * @param _port           int
     * @param _bootNodes      String[]
     * @param _upnpEnable     boolean
     * @param _maxTempNodes   int
     * @param _maxActiveNodes int
     * @param _showStatus     boolean
     * @param _showLog        boolean
     */
    public P2pMgr(int _netId, String _revision, String _nodeId, String _ip, int _port, final String[] _bootNodes,
                  boolean _upnpEnable, int _maxTempNodes, int _maxActiveNodes, boolean _showStatus, boolean _showLog,
                  boolean _bootlistSyncOnly, int _errorTolerance) {
        this.selfNetId = _netId;
        this.selfRevision = _revision;
        this.selfNodeId = _nodeId.getBytes();
        this.selfNodeIdHash = Arrays.hashCode(selfNodeId);
        this.selfShortId = new String(Arrays.copyOfRange(_nodeId.getBytes(), 0, 6));
        this.selfIp = Node.ipStrToBytes(_ip);
        this.selfPort = _port;
        this.upnpEnable = _upnpEnable;
        this.maxTempNodes = _maxTempNodes;
        this.maxActiveNodes = Math.max(_maxActiveNodes, 128);
        this.showStatus = _showStatus;
        this.showLog = _showLog;
        this.syncSeedsOnly = _bootlistSyncOnly;
        this.errTolerance = _errorTolerance;

        for (String _bootNode : _bootNodes) {
            Node node = Node.parseP2p(_bootNode);
            if (node != null && validateNode(node)) {
                tempNodes.add(node);
                seedIps.add(node.getIpStr());
            }
        }

        this.scheduledWorkers = new ScheduledThreadPoolExecutor(1);
        int cores = Runtime.getRuntime().availableProcessors();
        //private AtomicInteger count = new AtomicInteger();
        this.processWorkers = new ThreadPoolExecutor(
                cores,
                cores * 2,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                r -> {
                    // return new Thread(r, "process-worker-" + count.incrementAndGet());
                    return new Thread(r, "process-worker");
                },
                new TaskDiscardPolicy()
        );
        // private AtomicInteger count = new AtomicInteger();
        this.writeWorkers = new ThreadPoolExecutor(
                cores,
                cores * 2,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                r -> {
                    //return new Thread(r, "write-worker-" + count.incrementAndGet());
                    return new Thread(r, "write-worker");
                },
                new TaskDiscardPolicy()
        );
    }

    /**
     * @param _node Node
     * @return boolean
     */
    private boolean validateNode(final INode _node) {
        if(_node == null)
            return false;

        // filter self
        boolean notSelfId = _node.getIdHash() != this.selfNodeIdHash;
        boolean notSameIpOrPort = !(Arrays.equals(selfIp, _node.getIp()) && selfPort == _node.getPort());

        // filter already active.
        boolean notActive = !activeNodes.containsKey(_node.getIdHash());

        return notSelfId && notSameIpOrPort && notActive;
    }

    /**
     * @param _channel SocketChannel
     * TODO: check option
     */
    private void configChannel(final SocketChannel _channel) throws IOException {
        _channel.configureBlocking(false);
        _channel.socket().setSoTimeout(TIMEOUT_MSG_READ);
//        _channel.socket().setKeepAlive(true);
        _channel.socket().setReceiveBufferSize(P2pConstant.RECV_BUFFER_SIZE);
        _channel.socket().setSendBufferSize(P2pConstant.SEND_BUFFER_SIZE);
    }

    /**
     * accept new connection
     */
    private void accept() {
        SocketChannel channel;
        try {
            channel = tcpServer.accept();
            configChannel(channel);
            SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);

            String ip = channel.socket().getInetAddress().getHostAddress();
            int port = channel.socket().getPort();

            if (syncSeedsOnly) {
                channel.close();
                return;
            }

            Node node = new Node(channel, ip);
            node.setChannel(channel);
            ChannelBuffer cb = new ChannelBuffer(showLog,8);
            sk.attach(cb);

            if (showLog)
                System.out.println("<p2p new-connection " + ip + ":" + port + ">");

        } catch (IOException e) {
            if (showLog)
                System.out.println("<p2p inbound-accept-io-exception>");
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * @param _sc SocketChannel
     * @throws IOException IOException
     */
    private void readHeader(final SocketChannel _sc, final ChannelBuffer _cb) throws IOException {

        int ret;
        do {
            ret = _sc.read(_cb.headerBuf);
        } while (ret > 0);

        if(_cb.headerBuf.position() == 0 && ret == -1) {
            System.out.println("read header return here !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ");
            return;
        }

        if (_cb.headerBuf.hasRemaining() && ret == -1)
            throw new IOException("read-header-eof");

        if (!_cb.headerBuf.hasRemaining())
            _cb.header = Header.decode(_cb.headerBuf.array());
    }

    /**
     * @param _sc SocketChannel
     * @throws IOException IOException
     */
    private void readBody(final SocketChannel _sc, final ChannelBuffer _cb) throws IOException {

        if (_cb.bodyBuf == null)
            _cb.bodyBuf = ByteBuffer.allocate(_cb.header.getLen());

        int ret;
        do{
            ret = _sc.read(_cb.bodyBuf);
        } while (ret > 0);

        if(_cb.bodyBuf.position() == 0 && _cb.bodyBuf.capacity() > 0 && ret == -1) {
            System.out.println("read body return here !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! ");
            return;
        }

        if (_cb.bodyBuf.hasRemaining() && ret == -1)
            throw new IOException("read-body-eof");

        if (!_cb.bodyBuf.hasRemaining())
            _cb.body = _cb.bodyBuf.array();

    }

    /**
     * @param _sk SelectionKey
     * @throws IOException IOException
     */
    private void read(final SelectionKey _sk) throws IOException {

        if (_sk.attachment() == null) {
            throw new IOException("attachment is null");
        }
        ChannelBuffer rb = (ChannelBuffer) _sk.attachment();

        // read header
        if (!rb.isHeaderCompleted()) {
            readHeader((SocketChannel) _sk.channel(), rb);
        }

        // read body
        if (rb.isHeaderCompleted() && !rb.isBodyCompleted()) {
            readBody((SocketChannel) _sk.channel(), rb);
        }

        if (!rb.isBodyCompleted())
            return;

        Header h = rb.header;
        byte[] bodyBytes = rb.body;
        rb.refreshHeader();
        rb.refreshBody();

        short ver = h.getVer();
        byte ctrl = h.getCtrl();
        byte act = h.getAction();
        int route = h.getRoute();

        boolean underRC = rb.shouldRoute(route,
                ((route == txBroadCastRoute) ? P2pConstant.READ_MAX_RATE_TXBC : P2pConstant.READ_MAX_RATE));

        if (!underRC) {
            if (showLog)
                System.out.println("<p2p over-called-route=" + ver + "-" + ctrl + "-" + act + " calls="
                        + rb.getRouteCount(route).count + " node=" + rb.displayId + ">");
            return;
        }

        // print route
        // System.out.println("read " + ver + "-" + ctrl + "-" + act);
        switch (ver) {
            case Ver.V0:
                switch (ctrl) {

                    // handle p2p messages
                    case Ctrl.NET:

                        processWorkers.execute(()-> handleP2pMsg(_sk, act, bodyBytes));

                        break;

                    // handle kernel messages
                    default:

                        if (!handlers.containsKey(route)){
                            if(showLog)
                                System.out.println("<p2p handlers-not-found route=" + route + " node=" + rb.displayId + ">");
                            return;
                        }

                        processWorkers.execute(()->handleKernelMsg(rb.nodeIdHash, route, rb.displayId, bodyBytes));

                        break;
                }
                break;
        }
    }

    /**
     * @return boolean
     * TODO: supported protocol selfVersions
     */
    private boolean handshakeRuleCheck(int _netId) {
        return _netId == selfNetId;
    }

    /**
     * @param _sk       SelectionKey
     * @param _act      ACT
     * @param _msgBytes byte[]
     */
    private void handleP2pMsg(final SelectionKey _sk, byte _act, final byte[] _msgBytes) {

        try{

            SocketChannel sc = (SocketChannel)_sk.channel();
            ChannelBuffer cb = (ChannelBuffer) _sk.attachment();

            switch (_act) {

                case Act.REQ_HANDSHAKE:
                    handleReqHandshake(sc, cb, _msgBytes);
                    break;

                case Act.RES_HANDSHAKE:
                    handleResHandshake(sc, cb, _msgBytes);
                    break;

                case Act.REQ_ACTIVE_NODES:
                    handleReqActiveNodes(sc, cb);
                    break;

                case Act.RES_ACTIVE_NODES:
                    if (syncSeedsOnly) return;
                    handleResActiveNodes(sc, cb, _msgBytes);
                    break;

                default:
                    if (showLog)
                        System.out.println("<p2p unknown-route act=" + _act + " node=" + cb.displayId + ">");
                    break;

            }

        } catch (ClassCastException e){
            _sk.cancel();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * @param _id        int - node id hash
     * @param _route     int
     * @param _msgBytes  byte[]
     */
    private void handleKernelMsg(int _id, int _route, String _nodeShortId, final byte[] _msgBytes) {

        Node node = activeNodes.get(_id);
        if(node == null){
            if(showLog) System.out.println("<p2p fail-handle-kernel-msg node-not-found=" + _nodeShortId + " route=" + _route + ">");
            return;
        }

        List<Handler> hs = handlers.get(_route);
        if (hs == null) {
            if(showLog) System.out.println("<p2p fail-handle-kernel-msg handlers-not-found=" + _nodeShortId + " route=" + _route + ">");
            return;
        }

        for (Handler hlr : hs) {
            if (hlr == null)
                continue;
            node.refreshTimestamp();
            processWorkers.submit(() -> hlr.receive(node.getIdHash(), node.getIdShort(), _msgBytes));
        }

    }

    /**
     * @param _sc        SocketChannel
     * @param _cb        ChannelBuffer
     * @param _msgBytes  byte[]
     */
    private void handleReqHandshake(final SocketChannel _sc, final ChannelBuffer _cb, final byte[] _msgBytes){
        processWorkers.execute(()->{
            ReqHandshake1 req = ReqHandshake1.decode(_msgBytes);
            if (req == null) {
                closeSocket(_sc, "req-handshake decode-error");
                return;
            }
            if (!handshakeRuleCheck(req.getNetId())) {
                closeSocket(_sc, "req-handshake rule-fail");
                return;
            }

            Node node = inboundNodes.remove(_cb.channelId);
            if(node == null) {
                closeSocket(_sc, "req-handshake node-not-found=" + Utility.bytesToHex(req.getNodeId()));
                return;
            }

            node.setId(req.getNodeId());
            node.setPort(req.getPort());
            String binaryVersion;
            try {
                binaryVersion = new String(req.getRevision(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                binaryVersion = "decode-fail";
            }
            node.setBinaryVersion(binaryVersion);
            addToActive(node, "inbound");
            writeWorkers.submit(new TaskWrite(writeWorkers, showLog, node.getIdShort(), node.getChannel(), new ResHandshake1(true, this.selfRevision), _cb));
        });
    }

    /**
     * @param _sc         SocketChannel
     * @param _cb         ChannelBuffer
     * @param _msgBytes   byte[]
     *
     */
    private void handleResHandshake(final SocketChannel _sc, final ChannelBuffer _cb, final byte[] _msgBytes){
        processWorkers.execute(()->{
            ResHandshake1 res = ResHandshake1.decode(_msgBytes);
            if(res == null){
                closeSocket(_sc, "res-handshake decode-error node=" + _cb.displayId);
                return;
            }

            Node node = outboundNodes.remove(_cb.nodeIdHash);
            if(node == null) {
                closeSocket(_sc, "res-handshake node-not-found=" + _cb.displayId);
                return;
            }

            if (res.getSuccess()) {
                node.setBinaryVersion(res.getBinaryVersion());
                addToActive(node, "outbound");
            } else {
                closeSocket(_sc, "res-handshake remote-denied");
            }
        });
    }

    /**
     * @param _sc SocketChannel
     * @param _cb ChannelBuffer
     */
    private void handleReqActiveNodes(final SocketChannel _sc, final ChannelBuffer _cb){
        processWorkers.execute(()->{
            Node node = activeNodes.get(_cb.nodeIdHash);
            if(node == null) {
                closeSocket(_sc, "req-active-nodes node-not-found=" + _cb.displayId);
                return;
            }
            ResActiveNodes res = new ResActiveNodes(new ArrayList<>(activeNodes.values()));
            writeWorkers.submit(new TaskWrite(writeWorkers, showLog, node.getIdShort(), node.getChannel(), res, _cb));
        });
    }

    /**
     * @param _sc          SocketChannel
     * @param _cb          ChannelBuffer
     * @param _msgBytes    byte[]
     * runnable to handle active nodes responding from remote
     */
    private void handleResActiveNodes(final SocketChannel _sc, final ChannelBuffer _cb, final byte[] _msgBytes){
        processWorkers.execute(()->{
            ResActiveNodes resActiveNodes = ResActiveNodes.decode(_msgBytes);
            if (resActiveNodes == null) {
                closeSocket(_sc, "res-active-nodes decode-error node=" + _cb.displayId);
                return;
            }

            Node node = activeNodes.get(_cb.nodeIdHash);
            if(node == null) {
                closeSocket(_sc, "res-active-nodes node-not-found=" + _cb.displayId);
                return;
            }

            node.refreshTimestamp();

            // TODO: dist sort / filter
            List<INode> ins = resActiveNodes.getNodes();
            for (INode in : ins) {
                if (this.tempNodes.size() >= this.maxTempNodes)
                    return;
                Node n = (Node)in;
                if (validateNode(n))
                    tempNodes.add(n);
            }
        });
    }

    /**
     * @param _node      Node
     * @param _origin   String
     */
    private void addToActive(final Node _node, final String _origin){
        if(activeNodes.size() >= maxActiveNodes){
            closeSocket(_node.getChannel(), _origin + " -> active, active full");
            return;
        }

        if(_node.getIdHash() == selfNodeIdHash){
            closeSocket(_node.getChannel(), _origin + " -> active, self-connected");
            return;
        }

        _node.setConnection(_origin);
        _node.setFromBootList(seedIps.contains(_node.getIpStr()));
        INode previous = activeNodes.putIfAbsent(_node.getIdHash(), _node);
        if (previous != null) {
            closeSocket(_node.getChannel(), _origin + " -> active, node " + previous.getIdShort() + " exits");
            return;
        }
        if (showLog)
            System.out.println("<p2p " +_origin + " -> active node-id=" + _node.getIdShort() + " ip=" + _node.getIpStr() + ">");
    }

    private void timeout(final Map<Integer, Node> _collection, String _collectionName, int _timeout) {
        Iterator<Map.Entry<Integer, Node>> inboundIt = _collection.entrySet().iterator();
        while (inboundIt.hasNext()) {
            try{
                Node node = inboundIt.next().getValue();
                if (System.currentTimeMillis() - node.getTimestamp() > _timeout) {
                    closeSocket(node.getChannel(), _collectionName + "-timeout ip=" + node.getIpStr());
                    inboundIt.remove();
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void timeoutActive() {
        long now = System.currentTimeMillis();

        OptionalDouble average = activeNodes.values().stream().mapToLong(n -> now - n.getTimestamp()).average();
        double timeout = average.orElse(4000) * 5;
        timeout = Math.max(10000, Math.min(timeout, 60000));
        if (showLog)
            System.out.printf("<p2p average-delay=%.0fms>\n", average.orElse(0));

        Iterator<Map.Entry<Integer, Node>> activeIt = activeNodes.entrySet().iterator();
        while (activeIt.hasNext()) {
            Node node = activeIt.next().getValue();

            if (now - node.getTimestamp() > timeout) {
                closeSocket(node.getChannel(), "active-timeout node=" + node.getIdShort() + " ip=" + node.getIpStr());
                activeIt.remove();
            }

            if (!node.getChannel().isConnected()) {
                closeSocket(node.getChannel(), "channel-already-closed node=" + node.getIdShort() + " ip=" + node.getIpStr());
                activeIt.remove();
            }
        }
    }

    @Override
    public void run() {
        try {
            selector = Selector.open();

            tcpServer = ServerSocketChannel.open();
            tcpServer.configureBlocking(false);
            tcpServer.socket().setReuseAddress(true);
            tcpServer.socket().bind(new InetSocketAddress(Node.ipBytesToStr(selfIp), selfPort));
            tcpServer.register(selector, SelectionKey.OP_ACCEPT);

            tInbound = new Thread(new TaskInbound(), "p2p-inbound");
            tInbound.setPriority(Thread.NORM_PRIORITY);
            tInbound.start();

            tConnectPeers = new Thread(new TaskConnect(), "p2p-connect");
            tConnectPeers.setPriority(Thread.NORM_PRIORITY);
            tConnectPeers.start();

            tClear = new Thread(new TaskClear(), "p2p-clear");
            tClear.setPriority(Thread.NORM_PRIORITY);
            tClear.start();

            if (upnpEnable) {
                scheduledWorkers.scheduleWithFixedDelay(new TaskUPnPManager(selfPort), 1, PERIOD_UPNP_PORT_MAPPING,
                        TimeUnit.MILLISECONDS);
            }

            if (!syncSeedsOnly)
                scheduledWorkers.scheduleWithFixedDelay(new TaskRequestActiveNodes(this), 5000,
                        PERIOD_REQUEST_ACTIVE_NODES, TimeUnit.MILLISECONDS);

            if (showLog)
                this.handlers.forEach((route, callbacks) -> {
                    Handler handler = callbacks.get(0);
                    Header h = handler.getHeader();
                    System.out.println("<p2p-handler route=" + route + " v-c-a=" + h.getVer() + "-" + h.getCtrl() + "-"
                            + h.getAction() + " name=" + handler.getClass().getSimpleName() + ">");
                });

            if (showStatus) {
                tShowStatus = new Thread(new TaskStatus(), "p2p-status");
                tShowStatus.setPriority(Thread.NORM_PRIORITY);
                tShowStatus.run();
            }

            if (!syncSeedsOnly){
                tGetActivePeers = new Thread(new TaskGetActiveNodes(), "p2p-active-peers");
                tGetActivePeers.setPriority(Thread.NORM_PRIORITY);
                tGetActivePeers.run();
            }

        } catch (IOException e) {
            if (showLog)
                System.out.println("<p2p tcp-server-io-exception>");
        }
    }

    @Override
    public INode getRandom() {
        int nodesCount = activeNodes.size();
        if (nodesCount > 0) {
            Random r = new Random(System.currentTimeMillis());
            List<Integer> keysArr = new ArrayList<>(activeNodes.keySet());
            try {
                int randomNodeKeyIndex = r.nextInt(keysArr.size());
                int randomNodeKey = keysArr.get(randomNodeKeyIndex);
                return activeNodes.get(randomNodeKey);
            } catch (Exception e) {
                System.out.println("<p2p get-random-exception>");
                return null;
            }
        } else
            return null;
    }

    @Override
    public Map<Integer, INode> getActiveNodes() {
        return new HashMap<>(activeNodes);
    }

    @Override
    public void register(final List<Handler> _cbs) {
        for (Handler _cb : _cbs) {
            Header h = _cb.getHeader();
            short ver = h.getVer();
            byte ctrl = h.getCtrl();
            if (Ver.filter(ver) != Ver.UNKNOWN && Ctrl.filter(ctrl) != Ctrl.UNKNOWN) {
                if (!selfVersions.contains(ver)) {
                    selfVersions.add(ver);
                }

                int route = h.getRoute();
                List<Handler> routeHandlers = handlers.get(route);
                if (routeHandlers == null) {
                    routeHandlers = new ArrayList<>();
                    routeHandlers.add(_cb);
                    handlers.put(route, routeHandlers);
                } else {
                    routeHandlers.add(_cb);
                }
            }
        }
    }

    @Override
    public void send(int _id, String _displayId, final Msg _msg) {
        Node node = activeNodes.get(_id);
        if (node != null) {
            SelectionKey sk = node.getChannel().keyFor(selector);
            if (sk != null) {
                Object attachment = sk.attachment();
                if (attachment != null)
                    writeWorkers.submit(
                            new TaskWrite(writeWorkers, showLog, node.getIdShort(), node.getChannel(), _msg, (ChannelBuffer) attachment));
            }
        }
    }

    @Override
    public void shutdown() {
        start.set(false);
        if(scheduledWorkers != null)
            scheduledWorkers.shutdown();
        if(tInbound != null) tInbound.interrupt();
        processWorkers.shutdown();
        if(tConnectPeers != null) tConnectPeers.interrupt();
        if(tGetActivePeers != null) tGetActivePeers.interrupt();
        if(tShowStatus != null) tShowStatus.interrupt();
        if(tClear != null) tClear.interrupt();
        writeWorkers.shutdown();
        for (List<Handler> hdrs : handlers.values()) {
            hdrs.forEach(Handler::shutDown);
        }
    }

    @Override
    public List<Short> versions() {
        return new ArrayList<>(selfVersions);
    }

    @Override
    public int chainId() {
        return selfNetId;
    }

    @Override
    public int getSelfIdHash() {
        return this.selfNodeIdHash;
    }

    @Override
    public void closeSocket(SocketChannel _sc, String _reason) {
        SelectionKey sk = _sc.keyFor(selector);
        sk.cancel();
        if(showLog)
            System.out.println("<p2p close-socket reason=" + _reason + ">");
    }

    @Override
    public boolean isShowLog() {
        return this.showLog;
    }

    @Override
    public void errCheck(int nodeIdHashcode, String _displayId) {
        // TODO
    }
}