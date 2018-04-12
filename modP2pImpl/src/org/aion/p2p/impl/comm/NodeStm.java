package org.aion.p2p.impl.comm;

public class NodeStm {

	public static final int INIT = 1 << 0, ACCEPTED = 1 << 1, CONNECTTED = 1 << 2, HS_DONE = 1 << 3, ACTIVE = 1 << 4,
			CLOSED = 1 << 5, END = 1 << 6;

	public static final int ALL = END - 1;

	public int stat = INIT;

	public void setInit() {
		stat = INIT;
	}

	public int setStat(int st) {
		this.stat |= st;
		return stat;
	}

	public int setAccepted() {
		this.stat = 0;
		this.stat |= ACCEPTED;
		return stat;
	}

	public int setConnected() {
		this.stat = 0;
		this.stat |= CONNECTTED;
		return stat;
	}

	public void setClosed() {
		stat = CLOSED;
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

	public boolean shouldHS() {
		return ((stat & HS_DONE) == 0) && ((stat & ACCEPTED) > 0 || (stat & CONNECTTED) > 0);
	}

	public String toString() {
		return String.format("%8s", Integer.toBinaryString(stat & 0xff));
	}

}
