package org.aion.p2p.impl.comm;

public class NodeStm {

	public static final int INIT = 1 << 0, ACCEPTED = 1 << 1, CONNECTTED = 1 << 2, HS = 1 << 3, HS_DONE = 1 << 4,
			ACTIVE = 1 << 5, CLOSED = 1 << 6;

	public int stat = INIT;

	public int setStat(int st) {
		this.stat |= st;
		return stat;
	}

	public int setStatOr(int st0) {
		this.stat |= st0;
		return stat;
	}

	public int setStatOr(int st0, int st1) {
		this.stat |= (st0 | st1);
		return stat;
	}

	public boolean hasStat(int st) {
		return (stat & st) > 0;

	}

}
