package org.aion.p2p.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BHTree {

	static class Roots extends HashMap<Long, LevelRoots> {

	}

	static class LevelRoots extends ArrayList<Node> {

		void addNode(Node _n, Roots roots) {

			Node currNode = null;

			boolean foundNode = false;
			for (Node n : this) {

				// if node exist, add into peers list.
				if (Arrays.compare(n.hash, _n.hash) == 0) {
					n.peersId.add(_n.peerId);
					foundNode = true;
					currNode = n;
					break;
				}

			}

			// otherwise , no existing node, just add itself
			if (!foundNode) {
				this.add(_n);
				currNode = _n;
			}

			// link parent.
			if (currNode.parent == null) {
				LevelRoots prevLevel = roots.get(_n.bn - 1);
				if (prevLevel != null) {
					for (Node n : prevLevel) {
						if (Arrays.compare(currNode.parentHash, n.hash) == 0) {
							currNode.parent = n;
							n.childs.add(currNode);
							break;
						}
					}
				}

			}

			// link child.
			if (currNode.childs.size() == 0) {
				LevelRoots nextLevel = roots.get(_n.bn + 1);
				if (nextLevel != null) {
					for (Node n : nextLevel) {
						if (Arrays.compare(n.parentHash, currNode.hash) == 0) {
							currNode.childs.add(n);
							n.parent = currNode;
							break;
						}
					}
				}
			}
		}
	}

	Roots roots = new Roots();

	static class Node {
		long bn;
		long td;

		byte[] hash;
		byte[] parentHash;

		int peerId;
		List<Integer> peersId = new ArrayList<>(1);

		Node parent;
		List<Node> childs = new ArrayList<>(1);

		Node(long bn, long td, byte[] hash, byte[] parentHash) {
			this.bn = bn;
			this.td = td;
			this.hash = hash;
			this.parentHash = parentHash;
		}
	}

	void put(Node n) {

		// check if have n level in roots.
		LevelRoots lr = roots.get(n.bn);

		// if level roots not exist , add new one and wrap node.
		if (lr == null) {
			LevelRoots lroots = new LevelRoots();
			lroots.addNode(n, roots);
			roots.put(n.bn, lroots);
			return;
		}
		// if level roots exist, add node and link 2 layer.
		else {
			lr.addNode(n, roots);
		}
	}

	boolean hasCommonRoot() {
		long lowerBN = roots.keySet().stream().min((a, b) -> {
			return (int) (a - b);
		}).get();

		long topBN = roots.keySet().stream().max((a, b) -> {
			return (int) (a - b);
		}).get();

		if (roots.get(lowerBN).size() == 1) {
			return true;
		}
		return false;
	}

}
