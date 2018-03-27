//
//package org.aion.p2p.tree;
//
//import org.aion.p2p.tree.BHTree.Node;
//import org.junit.jupiter.api.Test;
//
//class BHTreeTest {
//
//	@Test
//	void test() {
//		BHTree bht = new BHTree();
//		Node n10 = new Node(10, 100, new byte[] { 0, 1, 2, 3 }, new byte[] { 10, 10, 10, 10 });
//		Node n11 = new Node(11, 110, new byte[] { 4, 5, 6, 7 }, n10.hash);
//		Node n12 = new Node(12, 120, new byte[] { 12, 5, 6, 7 }, n11.hash);
//		bht.put(n10);
//		bht.put(n12);
//		bht.put(n11);
//	}
//
//}
