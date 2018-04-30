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
package org.aion.p2p.impl3;

import org.aion.p2p.Header;
import org.aion.p2p.Msg;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author chris
 *
 */
class ChannelBuffer {

    class RouteStatus {
        long timestamp;
        int count;
        RouteStatus(){
            this.timestamp = System.currentTimeMillis();
            count = 0;
        }
    }

    private Map<Integer, RouteStatus> routes = new HashMap<>();

    private boolean showLog;

    private int nodeIdHash = 0;

    private String displayId = "";

	private ByteBuffer headerBuf = ByteBuffer.allocate(Header.LEN);

	private ByteBuffer bodyBuf = null;

	private Header header = null;

	private byte[] body = null;

	AtomicBoolean onWrite = new AtomicBoolean(false);

    ArrayBlockingQueue<Msg> outQueue;

	ChannelBuffer(boolean _showLog, int _messageSize){
	    this.showLog = _showLog;
	    this.outQueue = new ArrayBlockingQueue<>(_messageSize);
    }

    void setNodeIdHash(int _nodeIdHash){
	    this.nodeIdHash = _nodeIdHash;
    }

    void setDisplayId(String _displayId){
        this.displayId = _displayId;
    }

    int getNodeIdHash(){
        return this.nodeIdHash;
    }

    String getDisplayId(){
        return this.displayId;
    }

    /**
     * @param _route          int
     * @param _maxReqsPerSec  int requests within 1 s
     * @return                boolean flag if under route control
     */
    synchronized boolean shouldRoute(int _route, int _maxReqsPerSec) {
        long now = System.currentTimeMillis();
        RouteStatus prev = routes.putIfAbsent(_route, new RouteStatus());
        if (prev != null) {
            if ((now - prev.timestamp) > 1000) {
                prev.count = 0;
                prev.timestamp = now;
                return true;
            }
            boolean shouldRoute = prev.count < _maxReqsPerSec;
            if(shouldRoute)
                prev.count++;

            if(showLog && !shouldRoute)
                System.out.println("<p2p route-cooldown=" + _route + " node=" + this.displayId + " count=" + prev.count + ">");

            return shouldRoute;
        } else
            return true;
    }

    RouteStatus getRouteCount(int _route){
        return routes.get(_route);
    }

	void refreshHeader(){
		headerBuf.clear();
		header = null;
	}

	void refreshBody(){
		bodyBuf = null;
		body = null;
	}

	/**
	 * @return boolean
	 */
	boolean isHeaderCompleted(){
		return header != null;
	}

	/**
	 * @return boolean
	 */
	boolean isBodyCompleted() {
		return this.header != null && this.body != null && body.length == header.getLen();
	}

}