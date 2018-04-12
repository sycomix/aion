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
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl1;

import org.aion.p2p.*;
import org.aion.p2p.impl.TaskRequestActiveNodes;
import org.aion.p2p.impl.TaskUPnPManager;
import org.aion.p2p.impl.comm.Act;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl.comm.NodeStm;
import org.aion.p2p.impl.zero.msg.*;
import org.apache.commons.collections4.map.LRUMap;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.concurrent.ArrayBlockingQueue;

// import org.aion.p2p.impl.one.msg.Hello;

/**
 * @author Chris p2p://{uuid}@{ip}:{port} TODO: 1) simplify id bytest to int, ip
 *         bytest to str 2) upnp protocal 3) framing
 */
public final class P2pMgr implements IP2pMgr {

	private final static int PERIOD_SHOW_STATUS = 10000;
	private final static int PERIOD_REQUEST_ACTIVE_NODES = 1000;
	private final static int PERIOD_CONNECT_OUTBOUND = 500;
	private final static int PERIOD_CLEAR = 1000;

	private final static int TIMEOUT_OUTBOUND_CONNECT = 10000;

	private final static int TIMEOUT_OUTBOUND_NODES = 10000;

	private final static int PERIOD_UPNP_PORT_MAPPING = 3600000;

	private final static int TIMEOUT_MSG_READ = 1000;

	private final int maxTempNodes;
	private final int maxActiveNodes;

	private final boolean syncSeedsOnly;
	private final boolean showStatus;
	private final boolean showLog;
	private final boolean printReport;
	private final String reportFolder;
	private final int selfNetId;
	private final String selfRevision;
	private final byte[] selfNodeId;
	private final int selfNodeIdHash;
	private final String selfShortId;
	private final byte[] selfIp;
	private final int selfPort;
	private final boolean upnpEnable;

	private final Map<Integer, List<Handler>> handlers = new ConcurrentHashMap<>();
	private final Set<Short> versions = new HashSet<>();

	private NodeMgr nodeMgr;
	private ServerSocketChannel tcpServer;
	private Selector selector;

	private ScheduledThreadPoolExecutor scheduledWorkers;

	// private Map<Integer, Node> allNid = new HashMap<>();

	private final Map<Integer, Integer> errCnt = Collections.synchronizedMap(new LRUMap<>(128));

	private int errTolerance;

	// private ConcurrentHashMap<SelectableChannel, Queue<MsgParse>> nioBuffer =
	// new ConcurrentHashMap<>();

	public static class MsgParse {

		public MsgParse(byte[] bs, int _cid) {
			fs = bs;
			cid = _cid;
		}

		byte[] fs;
		int cid;
	}

	enum Dest {
		INBOUND, OUTBOUND, ACTIVE;
	}

	private static class MsgOut {
		public MsgOut(int _nid, Msg _msg, Dest _dst) {
			nid = _nid;
			msg = _msg;
			dest = _dst;
		}

		int nid;
		Msg msg;
		Dest dest;
	}

	private static class MsgIn {
		public MsgIn(int nid, String nsid, int route, byte[] msg) {
			this.nid = nid;
			this.nsid = nsid;
			this.route = route;
			this.msg = msg;
		}

		int nid;
		String nsid;
		int route;
		byte[] msg;
	}

	private ArrayBlockingQueue<MsgOut> sendMsgQue = new ArrayBlockingQueue<>(10240);

	private ArrayBlockingQueue<MsgIn> receiveMsgQue = new ArrayBlockingQueue<>(10240);

	private AtomicBoolean start = new AtomicBoolean(true);

	// initialed after handlers registration completed
	private static ReqHandshake1 cachedReqHandshake1;
	private static ResHandshake1 cachedResHandshake1;

	private List<ChannelBuffer> cbs = new CopyOnWriteArrayList<>();

	private final class TaskInbound implements Runnable {
		@Override
		public void run() {

			final int READ_BUFFER_SIZE = 512 * 1024;
			final ByteBuffer readBuf = ByteBuffer.allocate(READ_BUFFER_SIZE);

			while (start.get()) {

				int num;
				try {
					 num = selector.select(1);
					//num = selector.selectNow();
				} catch (IOException e) {
					if (showLog)
						System.out.println("<p2p inbound-select-io-exception>");
					continue;
				}

				if (num == 0) {
//					try {
//						Thread.sleep(0, 1);
//					} catch (Exception e) {
//					}
					continue;
				}

				Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

				while (keys.hasNext() && (num-- > 0)) {

					final SelectionKey sk = keys.next();
					keys.remove();

					SelectableChannel sc = (SelectableChannel) sk.channel();

					if (!sk.isValid())
						continue;

					if (sk.isAcceptable())
						accept();

					if (sk.isReadable()) {

						ChannelBuffer chanBuf = (ChannelBuffer) (sk.attachment());

						if (!cbs.contains(chanBuf))
							cbs.add(chanBuf);

						readBuf.rewind();
						try {

							int ret;
							int cnt = 0;

							while ((ret = ((SocketChannel) sc).read(readBuf)) > 0) {
								cnt += ret;
							}

							// read empty select key, continue.
							if (cnt <= 0) {
								continue;
							}

							byte[] bb = new byte[readBuf.position()];

							readBuf.position(0);
							readBuf.get(bb);

							chanBuf.inQueue.offer(new MsgParse(bb, sk.channel().hashCode()));

						} catch (NullPointerException e) {

							if (showLog) {
								System.out.println("<p2p read-msg-null-exception>");
							}

							closeSocket((SocketChannel) sk.channel());

							chanBuf.isClosed.set(true);
						} catch (P2pException e) {

							if (showLog) {
								System.out.println("<p2p read-msg-P2p-exception>");
							}

							// e.printStackTrace();

							// continue;

							closeSocket((SocketChannel) sk.channel());
							chanBuf.isClosed.set(true);

						} catch (ClosedChannelException e) {
							if (showLog) {
								System.out.println("<p2p readfail-closechannel>");
							}
							closeSocket((SocketChannel) sk.channel());

						} catch (IOException e) {

							if (showLog) {
								System.out.println("<p2p read-msg-io-exception: " + e.getMessage() + ">");
							}

							closeSocket((SocketChannel) sk.channel());
							chanBuf.isClosed.set(true);

						} catch (CancelledKeyException e) {
							if (showLog) {
								System.out.println("<p2p key-cancelled-exception>");
							}

							chanBuf.isClosed.set(true);
							closeSocket((SocketChannel) sk.channel());

						}
					}
				}
			}
			if (showLog)
				System.out.println("<p2p-pi shutdown>");
		}
	}

	void inParser() {

		while (true) {

			try {
				Thread.sleep(10);
			} catch (Exception e) {

			}
			ChannelBuffer chanBuf = null;
			// done.
			try {

				// Set<Map.Entry<SelectableChannel, Queue<MsgParse>>> scs =
				// nioBuffer.entrySet();
				List<Node> nds = P2pMgr.this.nodeMgr.allStmNodes;

				WAIT_2: for (ChannelBuffer cb : cbs) {

					if (cb.isClosed.get())
						cbs.remove(cb);

					chanBuf = cb;

					Queue<MsgParse> q = cb.inQueue;

					MsgParse mp = null;

					// System.out.println("nio parser qeueu len:" + q.size());

					while ((mp = q.peek()) != null) {

						if (cb.buffRemain == 0) {

							int cnt = 0;
							int prevCnt = mp.fs.length;

							do {
								cnt = read(mp.cid, mp.fs, cb, prevCnt);

								if (prevCnt == cnt) {
									break;
								} else
									prevCnt = cnt;

							} while (cnt > 0);

							// check if really read data.
							if (cnt > prevCnt) {
								cb.buffRemain = 0;
								throw new P2pException(
										"IO read overflow!  suppose read:" + prevCnt + " real left:" + cnt);
							}

							if (cnt == 0) {
								q.remove();
							}

							// System.out.println("nio parser single block len:"
							// + mp.fs.length + " before
							// rem:"
							// + cb.buffRemain + " after rem:" + cnt);

							cb.buffRemain = cnt;

						} else {

							if (q.size() < 2)
								continue WAIT_2;

							mp = q.poll();
							MsgParse mp1 = q.peek();

							// System.out
							// .println("nio parser 2block len:" + mp1.fs.length
							// + " remain:" +
							// cb.buffRemain);

							if (cb.buffRemain > mp.fs.length) {
								throw new P2pException(
										"IO parse overflow!  buff len:" + mp.fs.length + " buff rem::" + cb.buffRemain);
							}

							int total = cb.buffRemain + mp1.fs.length;
							ByteBuffer bb = ByteBuffer.allocate(total);
							try {
								// bb.put(mp.fs, cb.buffRemain, mp.fs.length
								// - cb.buffRemain);
								// bb.put(mp.fs, mp.fs.length -
								// cb.buffRemain, cb.buffRemain);
								bb.put(mp.fs, mp.fs.length - cb.buffRemain, cb.buffRemain);

							} catch (Exception e) {
								e.printStackTrace();
							}

							try {
								// bb.put(mp.fs, cb.buffRemain, mp.fs.length
								// - cb.buffRemain);
								// bb.put(mp.fs, mp.fs.length -
								// cb.buffRemain, cb.buffRemain);

								bb.put(mp1.fs);
							} catch (Exception e) {
								e.printStackTrace();
							}

							int cnt = 0;
							int prevCnt = total;
							do {
								cnt = read(mp.cid, bb.array(), cb, prevCnt);

								if (prevCnt == cnt) {
									break;
								} else
									prevCnt = cnt;

							} while (cnt > 0);

							// check if really read data.
							if (cnt > prevCnt) {
								cb.buffRemain = 0;
								throw new P2pException(
										"IO read overflow!  suppose read:" + prevCnt + " real left:" + cnt);
							}

							// System.out.println("nio parser 2block len:" +
							// mp1.fs.length + " before rem:"
							// + cb.buffRemain + " after rem:" + cnt);

							cb.buffRemain = 0;

							if (cnt == 0) {
								q.remove();
							} else {
								// mp1.cb.buffRemain = cnt;
								// mp1.fs = bb.array();

								byte[] tmp = new byte[cnt];
								bb.position(bb.capacity() - cnt);
								bb.get(tmp);
								// mp1.cb.buffRemain = 0;
								mp1.fs = tmp;

								// System.out.println("nio parser 2block
								// finished with : array:" + mp1.fs.length
								// + " rem:"
								// + mp1.cb.buffRemain);
							}

						}
					}
				}

			} catch (NullPointerException e) {

				if (showLog) {
					System.out.println("<p2p read-msg-null-exception>");
				}

				// closeSocket((SocketChannel) sk.channel());

				chanBuf.isClosed.set(true);
			} catch (P2pException e) {

				if (showLog) {
					System.out.println("<p2p read-msg-P2p-exception>");
				}

				// e.printStackTrace();

				// continue;

				// closeSocket((SocketChannel) sk.channel());
				chanBuf.isClosed.set(true);

			} catch (ClosedChannelException e) {
				if (showLog) {
					System.out.println("<p2p readfail-closechannel>");
				}
				// closeSocket((SocketChannel) sk.channel());

			} catch (IOException e) {

				if (showLog) {
					System.out.println("<p2p read-msg-io-exception: " + e.getMessage() + ">");
				}

				e.printStackTrace();

				// closeSocket((SocketChannel) sk.channel());
				chanBuf.isClosed.set(true);
			}
		}
	}

	private final class TaskSend implements Runnable {
		@Override
		public void run() {

			while (true) {
				try {
					MsgOut mo = sendMsgQue.take();

					Node node = null;
					switch (mo.dest) {
					case ACTIVE:
						node = nodeMgr.getActiveNode(mo.nid);
						break;
					case INBOUND:
						node = nodeMgr.getStmNode(mo.nid, NodeStm.ACCEPTED);
						if (node == null) {
							node = nodeMgr.getStmNode(mo.nid, NodeStm.CONNECTTED);
						}
						break;
					case OUTBOUND:
						node = nodeMgr.getStmNode(mo.nid, NodeStm.CONNECTTED);
						if (node == null) {
							node = nodeMgr.getStmNode(mo.nid, NodeStm.ACCEPTED);
						}
						break;
					}

					// if still not found , let's try all nodes.
					if (node == null) {
						// node = allNid.get(mo.nid);
						node = nodeMgr.getStmNode(mo.nid, NodeStm.ACTIVE);
					}
					if (node != null) {
						SelectionKey sk = node.getChannel().keyFor(selector);

						if (sk != null) {
							Object attachment = sk.attachment();
							if (attachment != null) {
								TaskWrite tw = new TaskWrite(showLog, node.getIdShort(), node.getChannel(), mo.msg,
										(ChannelBuffer) attachment, P2pMgr.this);
								tw.run();
							}
						}
					} else {
						System.out.println(
								"send msg, failed to find node! M:" + mo.msg + " D:" + mo.dest + " CID:" + mo.nid);
					}
				} catch (InterruptedException e) {
					System.out.println("Task send interrupted");
					break;
				}
			}
		}
	}

	private final class TaskReceive implements Runnable {
		@Override
		public void run() {

			while (true) {
				try {
					MsgIn mi = receiveMsgQue.take();

					List<Handler> hs = handlers.get(mi.route);
					if (hs == null)
						return;
					for (Handler hlr : hs) {
						if (hlr == null)
							continue;
						try {
							hlr.receive(mi.nid, mi.nsid, mi.msg);
						} catch (Exception e) {
							System.out.println("Exception during kernel message handling:");
							e.printStackTrace();
						}
					}
				} catch (InterruptedException e) {
					System.out.println("Task receive interrupted");
					break;
				}
			}
		}
	}

	private final class TaskStatus implements Runnable {
		@Override
		public void run() {
			Thread.currentThread().setName("p2p-ts");
			String status = nodeMgr.dumpNodeInfo(selfShortId);
			System.out.println(status);
			if (printReport) {
				try {
					Files.write(Paths.get(reportFolder, System.currentTimeMillis() + "-p2p-report.out"),
							status.getBytes());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			// nodeMgr.dumpAllNodeInfo();
		}
	}

	private final class TaskConnectPeers implements Runnable {
		@Override
		public void run() {
			Thread.currentThread().setName("p2p-tcp");
			while (start.get()) {
				try {
					Thread.sleep(PERIOD_CONNECT_OUTBOUND);
				} catch (InterruptedException e) {
					if (showLog)
						System.out.println("<p2p-tcp interrupted>");
				}

				if (nodeMgr.activeNodesSize() >= maxActiveNodes) {
					if (showLog)
						System.out.println("<p2p-tcp-connect-peer pass max-active-nodes>");

					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						if (showLog)
							System.out.println("<p2p-tcp-interrupted>");
						return;
					}
					continue;
				}

				Node node;

				node = nodeMgr.getRandomInitNode();

				if (node == null)
					continue;
				// if (node.getIfFromBootList())
				// nodeMgr.tempNodesAdd(node);
				// if (node.peerMetric.shouldNotConn()) {
				// continue;
				// }

				// int nodeIdHash = node.getIdHash();
				int cid = node.getCid();
				// if (!nodeMgr.getOutboundNodes().containsKey(nodeIdHash) &&
				// !nodeMgr.hasActiveNode(nodeIdHash)) {
				if (cid < 0 || nodeMgr.getStmNode(cid, NodeStm.CONNECTTED) == null) {
					int _port = node.getPort();
					try {
						SocketChannel channel = SocketChannel.open();
						if (showLog)
							System.out.println("<p2p try-connect-" + node.getIpStr() + ">");
						channel.socket().connect(new InetSocketAddress(node.getIpStr(), _port),
								TIMEOUT_OUTBOUND_CONNECT);
						configChannel(channel);

						if (channel.finishConnect() && channel.isConnected()) {

							SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);

							ChannelBuffer rb = new ChannelBuffer();

							node.setChannel(channel);
							node.setPortConnected(channel.socket().getLocalPort());

							node.st.setConnected();

							rb.cid = node.getCid();
							sk.attach(rb);

							try {
								Thread.sleep(1000);
							} catch (Exception e) {
							}
							sendMsgQue.offer(new MsgOut(node.getCid(), cachedReqHandshake1, Dest.OUTBOUND));

							// sendMsgQue.offer(new MsgOut(node.getCid(),
							// cachedReqHandshake, Dest.OUTBOUND));

							if (showLog)
								System.out.println("<p2p action=connect-outbound addr=" + node.getIpStr() + ":" + _port
										+ " result=success>");

							node.peerMetric.decFailedCount();

						} else {
							channel.close();
							node.peerMetric.incFailedCount();
						}
					} catch (IOException e) {
						if (showLog)
							System.out.println("<p2p action=connect-outbound addr=" + node.getIpStr() + ":" + _port
									+ " result=failed>");
						node.peerMetric.incFailedCount();
					}
				}
			}
		}
	}

	private final class TaskGuard implements Runnable {
		@Override
		public void run() {
			Thread.currentThread().setName("p2p-clr");
			while (start.get()) {
				try {
					Thread.sleep(PERIOD_CLEAR);

					// reconnect node stuck during handshake.
					List<Node> ns = nodeMgr.getStmNodeHS();
					for (Node n : ns) {
						if (n.getCid() > 0)
							P2pMgr.this.sendMsgQue.add(new MsgOut(n.getCid(), cachedReqHandshake1, Dest.OUTBOUND));
					}

					// remove closed channel.
					nodeMgr.removeClosed();
					
					nodeMgr.removeDups();

					// nodeMgr.rmTimeOutInbound(P2pMgr.this);
					//
					// // clean up temp nodes list if metric failed.
					// nodeMgr.rmMetricFailedNodes();
					//
					// Iterator outboundIt =
					// nodeMgr.getOutboundNodes().keySet().iterator();
					// while (outboundIt.hasNext()) {
					//
					// Object obj = outboundIt.next();
					//
					// if (obj == null)
					// continue;
					//
					// int nodeIdHash = (int) obj;
					// Node node = nodeMgr.getOutboundNodes().get(nodeIdHash);
					//
					// if (node == null)
					// continue;
					//
					// if (System.currentTimeMillis() - node.getTimestamp() >
					// TIMEOUT_OUTBOUND_NODES) {
					// closeSocket(node.getChannel());
					// outboundIt.remove();
					//
					// if (showLog)
					// System.out.println("<p2p-clear outbound-timeout>");
					// }
					// }
					//
					// nodeMgr.rmTimeOutActives(P2pMgr.this);

				} catch (Exception e) {
				}
			}
		}
	}

	/**
	 * @param _nodeId
	 *            byte[36]
	 * @param _ip
	 *            String
	 * @param _port
	 *            int
	 * @param _bootNodes
	 *            String[]
	 * @param _upnpEnable
	 *            boolean
	 * @param _maxTempNodes
	 *            int
	 * @param _maxActiveNodes
	 *            int
	 * @param _showStatus
	 *            boolean
	 * @param _showLog
	 *            boolean
	 */
	public P2pMgr(int _netId, String _revision, String _nodeId, String _ip, int _port, final String[] _bootNodes,
			boolean _upnpEnable, int _maxTempNodes, int _maxActiveNodes, boolean _showStatus, boolean _showLog,
			boolean _bootlistSyncOnly, boolean _printReport, String _reportFolder, int _errorTolerance) {
		this.selfNetId = _netId;
		this.selfRevision = _revision;
		this.selfNodeId = _nodeId.getBytes();
		this.selfNodeIdHash = Arrays.hashCode(selfNodeId);
		this.selfShortId = new String(Arrays.copyOfRange(_nodeId.getBytes(), 0, 6));
		this.selfIp = Node.ipStrToBytes(_ip);
		this.selfPort = _port;
		this.upnpEnable = _upnpEnable;
		this.maxTempNodes = _maxTempNodes;
		this.maxActiveNodes = _maxActiveNodes;
		this.showStatus = _showStatus;
		this.showLog = _showLog;
		this.syncSeedsOnly = _bootlistSyncOnly;
		this.printReport = _printReport;
		this.reportFolder = _reportFolder;
		this.errTolerance = _errorTolerance;

		nodeMgr = new NodeMgr(selfNodeIdHash);

		for (String _bootNode : _bootNodes) {
			Node node = Node.parseP2p(_bootNode);
			if (node != null && this.nodeMgr.validateNode(node, this.selfNodeIdHash, this.selfIp, this.selfPort)) {
				nodeMgr.addStmNodeForBoot(node);
				nodeMgr.seedIpAdd(node.getIpStr());
			}
		}

		// rem out for bug:
		// nodeMgr.loadPersistedNodes();
		cachedResHandshake1 = new ResHandshake1(true, this.selfRevision);
	}

	/**
	 * @param _node
	 *            Node
	 * @return boolean
	 */

	/**
	 * @param _channel
	 *            SocketChannel TODO: check option
	 */
	private void configChannel(final SocketChannel _channel) throws IOException {
		_channel.configureBlocking(false);
		_channel.socket().setSoTimeout(TIMEOUT_MSG_READ);
		// _channel.socket().setSendBufferSize(204800);
		// _channel.socket().setReceiveBufferSize(204800);
		_channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
		// _channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
		// _channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
	}

	/**
	 * @param _sc
	 *            SocketChannel
	 */
	public void closeSocket(final SocketChannel _sc) {
		if (showLog)
			System.out.println("<p2p close-socket->");

		try {
			SelectionKey sk = _sc.keyFor(selector);
			_sc.close();
			if (sk != null)
				sk.cancel();
		} catch (IOException e) {
			if (showLog)
				System.out.println("<p2p close-socket-io-exception>");
		}

		Node n = this.nodeMgr.getStmNode(_sc.hashCode(), NodeStm.ALL);

		if (n != null)
			n.st.setClosed();
	}

	private void accept() {
		SocketChannel channel;
		try {
			channel = tcpServer.accept();
			configChannel(channel);

			SelectionKey sk = channel.register(selector, SelectionKey.OP_READ);

			String ip = channel.socket().getInetAddress().getHostAddress();
			int port = channel.socket().getPort();

			if (syncSeedsOnly && nodeMgr.isSeedIp(ip)) {
				// close the channel and return.
				channel.close();
				return;
			}

			// Node node = new Node(false, ip);
			Node node = nodeMgr.allocNode(ip, 0, port);

			node.setChannel(channel);
			node.st.setAccepted();

			ChannelBuffer cb = new ChannelBuffer();
			cb.cid = node.getCid();
			sk.attach(cb);

			if (showLog)
				System.out.println("<p2p new-connection " + ip + ":" + port + ">");

		} catch (IOException e) {
			if (showLog)
				System.out.println("<p2p inbound-accept-io-exception>");
			return;
		}

	}

	/**
	 * SocketChannel
	 * 
	 * @throws IOException
	 *             IOException
	 */
	private int readHeader(final ChannelBuffer _cb, ByteBuffer readBuffer, int cnt) throws IOException {

		// int ret;
		// while ((ret = _sc.read(_cb.headerBuf)) > 0) {
		// }

		if (cnt < Header.LEN)
			return cnt;

		// int origPos = readBuffer.position();
		//
		// int startP = origPos - cnt;
		//
		// readBuffer.position(startP);

		_cb.readHead(readBuffer);

		// readBuffer.position(origPos);

		return cnt - Header.LEN;

	}

	/**
	 * SocketChannel
	 * 
	 * @throws IOException
	 *             IOException
	 */
	private int readBody(final ChannelBuffer _cb, ByteBuffer readBuffer, int cnt) throws IOException {

		int bodyLen = _cb.header.getLen();

		// some msg have nobody.
		if (bodyLen == 0) {
			_cb.body = new byte[0];
			return cnt;
		}

		if (cnt < bodyLen)
			return cnt;

		// int origPos = readBuffer.position();
		// int startP = origPos - cnt;

		// readBuffer.position(startP);

		_cb.readBody(readBuffer);

		// readBuffer.position(origPos);

		return cnt - bodyLen;
	}

	/**
	 * @param _sk
	 *            SelectionKey
	 * @throws IOException
	 *             IOException
	 */
	private int read(int cid, byte[] bs, ChannelBuffer rb, int cnt) throws IOException {

		ByteBuffer readBuffer = ByteBuffer.wrap(bs);

		int currCnt = 0;

		// read header
		if (!rb.isHeaderCompleted()) {
			currCnt = readHeader(rb, readBuffer, cnt);
		} else {
			currCnt = cnt;
		}

		// if(rb.isHeaderCompleted() &&
		// !handlers.containsKey(rb.header.getRoute())){
		// // TODO: Test
		// return;
		// }

		// read body
		if (rb.isHeaderCompleted() && !rb.isBodyCompleted()) {
			currCnt = readBody(rb, readBuffer, currCnt);
		}

		if (!rb.isBodyCompleted())
			return currCnt;

		Header h = rb.header;

		// byte[] bodyBytes = Arrays.copyOf(rb.body, rb.body.length);

		byte[] bodyBytes = rb.body;
		rb.refreshHeader();
		rb.refreshBody();

		short ver = h.getVer();
		byte ctrl = h.getCtrl();
		byte act = h.getAction();

		switch (ver) {
		case Ver.V0:
			switch (ctrl) {
			case Ctrl.NET:
				handleP2pMsg(cid, rb, act, bodyBytes);
				break;
			default:
				int route = h.getRoute();
				if (rb.cid > 0 || handlers.containsKey(route))
					handleKernelMsg(rb.cid, route, bodyBytes);
				break;
			}
			break;

		}

		return currCnt;

	}

	/**
	 * @return boolean TODO: implementation
	 */
	private boolean handshakeRuleCheck(int netId) {

		// check net id
		if (netId != selfNetId)
			return false;

		// check supported protocol versions
		return true;
	}

	private void handleReqHandshake(final ChannelBuffer _buffer, int cid, final byte[] _nodeId, int _netId, int _port,
			final byte[] _revision) {

		Node node = nodeMgr.getStmNode(cid, NodeStm.ACCEPTED);

		if (node != null && node.getCid() > 0 && node.peerMetric.notBan()) {
			if (handshakeRuleCheck(_netId)) {
				node.setId(_nodeId);
				node.setPort(_port);

				// handshake 1
				if (_revision != null) {
					String binaryVersion;
					try {
						binaryVersion = new String(_revision, "UTF-8");
					} catch (UnsupportedEncodingException e) {
						binaryVersion = "decode-fail";
					}
					node.setBinaryVersion(binaryVersion);
					sendMsgQue.offer(new MsgOut(node.getCid(), cachedResHandshake1, Dest.INBOUND));
				}

				node.st.setStat(NodeStm.HS_DONE);

				node.st.setStat(NodeStm.ACTIVE);

				// nodeMgr.moveInboundToActive(cid, this);
			} else {
				if (isShowLog())
					System.out.println("incompatible netId ours=" + this.selfNetId + " theirs=" + _netId);
			}
		}
	}

	private void handleResHandshake(int cid, String _binaryVersion) {

		Node node = nodeMgr.getStmNode(cid, NodeStm.CONNECTTED);

		if (node != null && node.getCid() > 0 && node.peerMetric.notBan()) {

			node.refreshTimestamp();
			node.setBinaryVersion(_binaryVersion);

			node.st.setStat(NodeStm.HS_DONE);

			node.st.setStat(NodeStm.ACTIVE);

			// nodeMgr.moveOutboundToActive(node.getCid(), node.getIdShort(), this);
		}
	}

	/**
	 * @param _sk
	 *            SelectionKey
	 * @param _act
	 *            ACT
	 * @param _msgBytes
	 *            byte[]
	 */
	private void handleP2pMsg(final int cid, ChannelBuffer rb, byte _act, final byte[] _msgBytes) {

		switch (_act) {

		case Act.REQ_HANDSHAKE:
			if (_msgBytes.length > ReqHandshake.LEN) {
				ReqHandshake1 reqHandshake1 = ReqHandshake1.decode(_msgBytes);
				if (reqHandshake1 != null) {
					handleReqHandshake(rb, cid, reqHandshake1.getNodeId(), reqHandshake1.getNetId(),
							reqHandshake1.getPort(), reqHandshake1.getRevision());
				}
			}
			break;

		case Act.RES_HANDSHAKE:

			if (rb.cid <= 0)
				return;

			if (_msgBytes.length > ResHandshake.LEN) {
				// System.out.println("receive handshake. nid:" + rb.cid + "
				// v1");

				ResHandshake1 resHandshake1 = ResHandshake1.decode(_msgBytes);
				if (resHandshake1 != null && resHandshake1.getSuccess())
					handleResHandshake(rb.cid, resHandshake1.getBinaryVersion());

			}
			break;

		case Act.REQ_ACTIVE_NODES:
			if (rb.cid <= 0)
				return;

			sendMsgQue.offer(new MsgOut(rb.cid, new ResActiveNodes(nodeMgr.getActiveNodesList()), Dest.ACTIVE));

			break;

		case Act.RES_ACTIVE_NODES:
			if (syncSeedsOnly)
				break;

			if (rb.cid > 0) {
				Node node = nodeMgr.getActiveNode(rb.cid);
				if (node != null) {
					node.refreshTimestamp();
					ResActiveNodes resActiveNodes = ResActiveNodes.decode(_msgBytes);
					if (resActiveNodes != null) {
						List<Node> incomingNodes = resActiveNodes.getNodes();
						for (Node incomingNode : incomingNodes) {
							if (nodeMgr.allStmNodes.size() >= this.maxTempNodes)
								return;
							if (this.nodeMgr.validateNode(incomingNode, this.selfNodeIdHash, this.selfIp,
									this.selfPort)) {
								nodeMgr.addStmNode(incomingNode);
							}
						}
					}
				}
			}
			break;
		default:
			// if (showLog)
			// System.out.println("<p2p unknown-route act=" + _act + ">");
			break;
		}
	}

	/**
	 * @param _nodeIdHash
	 *            int
	 * @param _route
	 *            int
	 * @param _msgBytes
	 *            byte[]
	 */
	private void handleKernelMsg(int cid, int _route, final byte[] _msgBytes) {
		Node node = nodeMgr.getActiveNode(cid);

		if (node == null)
			node = nodeMgr.getStmNode(cid, NodeStm.ACTIVE);

		// fail back to nid search.
		if (node == null)
			node = nodeMgr.getStmNodeByNid(cid, NodeStm.ACTIVE);

		// fail back to inbound
		// if (node == null) {
		// //node = nodeMgr.getInboundNode(cid);
		// nodeMgr.getStmNode(cid, NodeStm.ACTIVE);
		// }

		// fail back to outbound
		// if (node == null) {
		// node = nodeMgr.getOutboundNode(cid);
		// }

		if (node != null) {
			// int nid = node.getIdHash();

			int nid = node.getCid();

			String nsid = node.getIdShort();

			node.refreshTimestamp();
			receiveMsgQue.offer(new MsgIn(nid, nsid, _route, _msgBytes));
		} else {
			System.out.println(" handle kernel msg failed. can't find node:" + cid);
		}
	}

	/**
	 * @return NodeMgr
	 */
	public NodeMgr getNodeMgr() {
		return this.nodeMgr;
	}

	@Override
	public void run() {
		try {
			selector = Selector.open();

			scheduledWorkers = new ScheduledThreadPoolExecutor(1);

			tcpServer = ServerSocketChannel.open();
			tcpServer.configureBlocking(false);
			tcpServer.socket().setReuseAddress(true);
			tcpServer.socket().bind(new InetSocketAddress(Node.ipBytesToStr(selfIp), selfPort));
			tcpServer.register(selector, SelectionKey.OP_ACCEPT);

			Thread thrdIn = new Thread(new TaskInbound(), "p2p-in");
			thrdIn.setPriority(Thread.NORM_PRIORITY);
			thrdIn.start();

			for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
				Thread thrdOut = new Thread(new TaskSend(), "p2p-out-" + i);
				thrdOut.setPriority(Thread.NORM_PRIORITY);
				thrdOut.start();
			}

			for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
				Thread t = new Thread(new TaskReceive(), "p2p-worker-" + i);
				t.setPriority(Thread.NORM_PRIORITY);
				t.start();
			}

			for (int i = 0; i < 1; i++) {
				Thread t = new Thread(new Runnable() {

					public void run() {
						inParser();
					}
				}, "p2p-in-parse-" + i);

				t.setPriority(Thread.NORM_PRIORITY);
				t.start();
			}
			if (upnpEnable)
				scheduledWorkers.scheduleWithFixedDelay(new TaskUPnPManager(selfPort), 1, PERIOD_UPNP_PORT_MAPPING,
						TimeUnit.MILLISECONDS);

			if (showStatus)
				scheduledWorkers.scheduleWithFixedDelay(new TaskStatus(), 2, PERIOD_SHOW_STATUS, TimeUnit.MILLISECONDS);

			if (!syncSeedsOnly)
				scheduledWorkers.scheduleWithFixedDelay(new TaskRequestActiveNodes(this), 5000,
						PERIOD_REQUEST_ACTIVE_NODES, TimeUnit.MILLISECONDS);

			Thread thrdClear = new Thread(new TaskGuard(), "p2p-clear");
			thrdClear.setPriority(Thread.NORM_PRIORITY);
			thrdClear.start();

			Thread thrdConn = new Thread(new TaskConnectPeers(), "p2p-conn");
			thrdConn.setPriority(Thread.NORM_PRIORITY);
			thrdConn.start();

		} catch (IOException e) {
			if (showLog)
				System.out.println("<p2p tcp-server-io-exception>");
		}
	}

	@Override
	public INode getRandom() {
		return nodeMgr.getRandom();
	}

	public Map<Integer, INode> getActiveNodes() {
		return this.nodeMgr.getActiveNodes();
	}

	/**
	 * for test
	 */
	// void clearTempNodes() {
	// this.nodeMgr.clearTempNodes();
	// }

	// public int getTempNodesCount() {
	// return nodeMgr.tempNodesSize();
	// }

	public void register(final List<Handler> _cbs) {
		for (Handler _cb : _cbs) {
			Header h = _cb.getHeader();
			short ver = h.getVer();
			byte ctrl = h.getCtrl();
			if (Ver.filter(ver) != Ver.UNKNOWN && Ctrl.filter(ctrl) != Ctrl.UNKNOWN) {
				if (!versions.contains(ver)) {
					versions.add(ver);
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

		List<Short> supportedVersions = new ArrayList<>(versions);
		cachedReqHandshake1 = new ReqHandshake1(selfNodeId, selfNetId, this.selfIp, this.selfPort,
				this.selfRevision.getBytes(), supportedVersions);
	}

	@Override
	public void send(int cid, final Msg _msg) {
		// Node node = this.nodeMgr.getActiveNode(cid);
		// if (node != null) {
		// SelectionKey sk = node.getChannel().keyFor(selector);
		//
		// if (sk != null) {
		// Object attachment = sk.attachment();
		// if (attachment != null)
		// workers.submit(new TaskWrite(workers, showLog, node.getIdShort(),
		// node.getChannel(), _msg,
		// (ChannelBuffer) attachment, this));
		// }
		// }

		sendMsgQue.offer(new MsgOut(cid, _msg, Dest.ACTIVE));
	}

	@Override
	public void shutdown() {
		start.set(false);
		scheduledWorkers.shutdownNow();
		nodeMgr.shutdown(this);

		for (List<Handler> hdrs : handlers.values()) {
			hdrs.forEach(hdr -> hdr.shutDown());
		}
	}

	@Override
	public List<Short> versions() {
		return new ArrayList<Short>(versions);
	}

	@Override
	public int chainId() {
		return selfNetId;
	}

	/**
	 * Remove an active node if exists.
	 *
	 * @param nodeIdHash
	 */
	public void removeActive(int nodeIdHash) {
		nodeMgr.removeActive(nodeIdHash, this);
	}

	@Override
	public void dropActive(Integer _nodeIdHash) {
		nodeMgr.dropActive(_nodeIdHash, this);
	}

	public boolean isShowLog() {
		return showLog;
	}

	@Override
	public void errCheck(int nodeIdHashcode, String _displayId) {
		int cnt = (errCnt.get(nodeIdHashcode) == null ? 1 : (errCnt.get(nodeIdHashcode).intValue() + 1));

		if (cnt > this.errTolerance) {
			ban(nodeIdHashcode);
			errCnt.put(nodeIdHashcode, 0);

			if (isShowLog()) {
				System.out.println("<ban node: " + (_displayId == null ? nodeIdHashcode : _displayId) + ">");
			}
		} else {
			errCnt.put(nodeIdHashcode, cnt);
		}
	}

	private void ban(int nodeIdHashcode) {
		nodeMgr.ban(nodeIdHashcode);
		nodeMgr.dropActive(nodeIdHashcode, this);
	}
}
