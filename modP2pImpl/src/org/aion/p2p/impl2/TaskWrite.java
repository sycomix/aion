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

package org.aion.p2p.impl2;

import org.aion.p2p.Header;
import org.aion.p2p.Msg;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author chris
 */
public class TaskWrite implements Runnable {

	private ThreadPoolExecutor writeWorkers;
	private boolean showLog;
	private String nodeShortId;
	private SocketChannel sc;
	private Msg msg;
	private ChannelBuffer channelBuffer;

	/**
	 * @param _writeWorkers    ExecutorService
	 * @param _showLog         boolean
	 * @param _nodeShortId     String
	 * @param _sc              SocketChannel
	 * @param _msg             Msg
	 * @param _cb              ChannelBuffer
	 */
	TaskWrite(
			final ThreadPoolExecutor _writeWorkers,
			boolean _showLog,
			String _nodeShortId,
			final SocketChannel _sc,
			final Msg _msg,
			final ChannelBuffer _cb
	) {
		this.writeWorkers = _writeWorkers;
		this.showLog = _showLog;
		this.nodeShortId = _nodeShortId;
		this.sc = _sc;
		this.msg = _msg;
		this.channelBuffer = _cb;
	}

	@Override
	public void run() {

		if (this.channelBuffer.onWrite.compareAndSet(false, true)) {
			byte[] bodyBytes = msg.encode();
			int bodyLen = bodyBytes == null ? 0 : bodyBytes.length;
			Header h = msg.getHeader();
			h.setLen(bodyLen);
			byte[] headerBytes = h.encode();

			int totalLen = headerBytes.length + bodyLen;
			// System.out.println("!!! write route=" + h.getVer() + "-" + h.getCtrl() + "-" + h.getAction() + " len=" + totalLen);
			ByteBuffer buf = ByteBuffer.allocate(totalLen);
			buf.put(headerBytes);
			if (bodyBytes != null)
				buf.put(bodyBytes);
			buf.flip();

			try {
				while (buf.hasRemaining()) {
					sc.write(buf);
				}
			} catch (IOException e) {
				if (showLog) {
					System.out.println("<p2p-write io-exception=" + e.getMessage() + " node=" + this.nodeShortId + ">");
				}
			} catch (Exception e){
			    if(showLog)
			        System.out.println("<p2p-write exeception=" + e.getMessage() + " node=" + this.nodeShortId + ">");
            } finally {
				this.channelBuffer.onWrite.set(false);
                Msg msg = this.channelBuffer.outQueue.poll();
                if (msg != null)
                    writeWorkers.submit(new TaskWrite(writeWorkers, showLog, nodeShortId, sc, msg, channelBuffer));
			}
		} else {
			try {
				boolean success = this.channelBuffer.outQueue.offer(msg);
				if (showLog){
				    if(success)
                        System.out.println(
                            "<p2p-write node=" + this.channelBuffer.displayId +
                            " msg-queued queue-size=" + this.channelBuffer.outQueue.size() + ">"
                        );
                    else
                        System.out.println(
                            "<p2p-write node=" + this.channelBuffer.displayId +
                            " msg-dropped queue-size=" + this.channelBuffer.outQueue.size() + ">"
                        );
                }
			} catch (Exception e){
				e.printStackTrace();
			}
		}
	}
}