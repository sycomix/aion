import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.evtmgr.impl.mgr.EventMgrA0;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.impl.comm.Node;
import org.aion.p2p.impl.comm.NodeMgr;
import org.aion.p2p.impl1.P2pMgr;
import org.aion.txpool.ITxPool;
import org.aion.txpool.common.AccountState;
import org.aion.txpool.zero.TxPoolA0;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.StandaloneBlockchain.Builder;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.aion.zero.impl.blockchain.PendingTxCache;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * Class used to measure the performance of the AionPendingStateImpl class.
 *
 * @author Nick Nadeau
 */
public class AionPendingStateImplBenchmark {
    private static Map<BenchmarkCondition, List<Long>> records;
    private static List<BenchmarkCondition> orderOfCalls;
    private static Map<Integer, Event> singleTransactionEvents;
    private static AionPendingStateImpl pendingState;

    // parameters for tuning.
    private static final int FEW_THREADS = 5;
    private static final int MANY_THREADS = 50;
    private static final int FEW_REQUESTS = 300;
    private static final int AVG_REQUESTS = 10_000;
    private static final int MANY_REQUESTS = 100_000;
    private static final int DEFAULT_NUM_THREADS = 50;
    private static final int DEFAULT_NUM_REQUESTS = 100_000;
    private static final int FEW_TXS = 20;
    private static final int MANY_TXS = 1_500;
    private static final int AVG_DATA_SIZE = 2_500;
    private static final int LARGE_DATA_SIZE = 50_000;
    private static final int AVG_BLOCK_HEIGHT = 10_000;
    private static final int FAR_BLOCK_HEIGHT = 100_000;
    private static final int CHAIN_EXTRA_HEIGHT = 75_000;
    private static final int DEFAULT_CHAIN_HEIGHT = 125_000;
    private static final int PENDING_TX_AVG_SIZE = 300;
    private static final int PENDING_TX_LARGE_SIZE = 30_000;
    private static final int OUTDATED_AVG_SIZE = 300;
    private static final int OUTDATED_LARGE_SIZE = 30_000;
    private static final int ACC_STATE_MAP_SIZE = 100;
    private static final int WRAPPER_SIZE = 100;
    private static final int DUMP_POOL_AVG_SIZE = 1_000;
    private static final int DUMP_POOL_LARGE_SIZE = 10_000;
    private static final int NUM_DUMP_POOL_REQUESTS = 50;
    private static final int FEW_PEERS = 50;
    private static final int MANY_PEERS = 500;
    private static final BigInteger LARGE_BIG_INT = BigInteger.TWO.pow(1_000);

    /**
     * enum to trigger a call to a specific method.
     */
    private enum Event {
        INST,

        GET_REPO_FEW_FEW, GET_REPO_FEW_AVG, GET_REPO_FEW_MANY, GET_REPO_MANY_FEW, GET_REPO_MANY_AVG,
        GET_REPO_MANY_MANY,

        GET_PENDING_TX,

        ADD_TX_AVG_DATA, ADD_TX_LARGE_DATA, ADD_TX_LARGE_NONCE, ADD_TX_LARGE_VALUE, ADD_TX_LARGE_NRG,
        ADD_TX_NULL_TO,

        ADD_FEW_TXS_AVG_DATA, ADD_FEW_TXS_LARGE_DATA, ADD_FEW_TXS_LARGE_NONCES,
        ADD_FEW_TXS_LARGE_VALUES, ADD_FEW_TXS_LARGE_NRGS, ADD_FEW_TXS_NULL_TOS, ADD_FEW_TXS_MIXED,
        ADD_MANY_TXS_AVG_DATA, ADD_MANY_TXS_LARGE_DATA, ADD_MANY_TXS_LARGE_NONCES,
        ADD_MANY_TXS_LARGE_VALUES, ADD_MANY_TXS_LARGE_NRGS, ADD_MANY_TXS_NULL_TOS,
        ADD_MANY_TXS_MIXED,

        SEED_PROCESS_FEW_TXS_AVG_DATA, SEED_PROCESS_FEW_TXS_LARGE_DATA,
        SEED_PROCESS_FEW_TXS_LARGE_NONCES, SEED_PROCESS_FEW_TXS_LARGE_VALUES,
        SEED_PROCESS_FEW_TXS_LARGE_NRGS, SEED_PROCESS_FEW_TXS_NULL_TOS, SEED_PROCESS_FEW_TXS_MIXED,
        SEED_PROCESS_MANY_TXS_AVG_DATA, SEED_PROCESS_MANY_TXS_LARGE_DATA,
        SEED_PROCESS_MANY_TXS_LARGE_NONCES, SEED_PROCESS_MANY_TXS_LARGE_VALUES,
        SEED_PROCESS_MANY_TXS_LARGE_NRGS, SEED_PROCESS_MANY_TXS_NULL_TOS,
        SEED_PROCESS_MANY_TXS_MIXED,

        ADD_PENDING_IMPL_AVG_DATA, ADD_PENDING_IMPL_LARGE_DATA, ADD_PENDING_IMPL_LARGE_NONCE,
        ADD_PENDING_IMPL_LARGE_VALUE, ADD_PENDING_IMPL_LARGE_NRG, ADD_PENDING_IMPL_NULL_TO,
        ADD_PENDING_IMPL_DIFF_NONCE,

        FIND_ANCESTOR_AVG_DIST_EQUIDISTANT_AT_TOP, FIND_ANCESTOR_AVG_DIST_EQUIDISTANT_NOT_TOP,
        FIND_ANCESTOR_AVG_DIST_NONEQUIDISTANT_AT_TOP, FIND_ANCESTOR_AVG_DIST_NONEQUIDISTANT_NOT_TOP,
        FIND_ANCESTOR_FAR_DIST_EQUIDISTANT_AT_TOP, FIND_ANCESTOR_FAR_DIST_EQUIDISTANT_NOT_TOP,
        FIND_ANCESTOR_FAR_DIST_NONEQUIDISTANT_AT_TOP, FIND_ANCESTOR_FAR_DIST_NONEQUIDISTANT_NOT_TOP,

        PROCESS_BEST_AVG_CHAIN, PROCESS_BEST_LONG_CHAIN,

        FLUSH_CACHE_AVG, FLUSH_CACHE_LARGE,

        CLEAR_OUTDATED_AVG, CLEAR_OUTDATED_LARGE,

        DUMP_POOL_AVG, DUMP_POOL_LARGE,

        GET_PEERS_BEST_FEW, GET_PEERS_BEST_MANY
    }

    /**
     * enum to force a call down a specific code path.
     */
    private enum CodePath {
        IS_SEED, IS_BACKUP, BUFFER_ENABLED
    }

    static {
        singleTransactionEvents = new HashMap<>();
        singleTransactionEvents.put(0, Event.ADD_TX_AVG_DATA);
        singleTransactionEvents.put(1, Event.ADD_TX_LARGE_VALUE);
        singleTransactionEvents.put(2, Event.ADD_TX_LARGE_DATA);
        singleTransactionEvents.put(3, Event.ADD_TX_LARGE_NONCE);
        singleTransactionEvents.put(4, Event.ADD_TX_LARGE_NRG);
        singleTransactionEvents.put(5, Event.ADD_TX_NULL_TO);
    }

    //TODO: make a clean method that completely tears down the APSI instance via all exposed fields.
    //TODO: just need some default objects to use.

    @Before
    public void setup() {
        records = new HashMap<>();
        orderOfCalls = new ArrayList<>();
        AionBlockchainImpl.inst().setEventManager(new EventMgrA0(new Properties())); //TODO: is this correct setup?
    }

    @Test
    public void testRandomizedBenchmarking() throws InterruptedException {
        makeCall(new BenchmarkCondition(Event.INST));
        setupEmptyBlockchain();
//        getRandomCallOrder();
        getCustomCallOrder();
        for (BenchmarkCondition condition : orderOfCalls) {
            makeCall(condition);
        }
        printRecords();
    }

    //<----------------------------METHODS FOR PERFORMANCE RECORDING------------------------------->

    /**
     * Calls inst().
     */
    private void recordInst(BenchmarkCondition condition) {
        long start = System.nanoTime();
        pendingState = AionPendingStateImpl.inst();
        long end = System.nanoTime();
        storeRecord(condition, end - start);
    }

    /**
     * Calls getRepository() a certain number of times by a certain number of threads - both of
     * which numbers are specified by the event inside condition.
     */
    private void recordGetRepository(BenchmarkCondition condition) {
        int numThreads = getNumThreadsForEvent(condition.event);
        int numRequests = getNumRequestsForEvent(condition.event);
        ExecutorService threads = Executors.newFixedThreadPool(numThreads);
        long start = System.nanoTime();
        for (int i = 0; i < numRequests; i++) {
            threads.execute(new GetRepoThread());
        }
        long end = System.nanoTime();
        threads.shutdown();
        storeRecord(condition, end - start);
    }

    /**
     * Calls getPendingTransactions().
     */
    private void recordGetPendingTransactions(BenchmarkCondition condition) {
        long start = System.nanoTime();
        pendingState.getPendingTransactions();
        long end = System.nanoTime();
        storeRecord(condition, end - start);
    }

    /**
     * Calls addPendingTransaction() using a transaction that corresponds to the event inside
     * condition.
     */
    private void recordAddPendingTransaction(BenchmarkCondition condition) throws InterruptedException {
        ExecutorService threads = Executors.newFixedThreadPool(DEFAULT_NUM_THREADS);
        AionTransaction transaction = getTransactionForEvent(condition.event);
        long start = System.nanoTime();
        for (int i = 0; i < DEFAULT_NUM_REQUESTS; i++) {
            threads.execute(new AddPendingTransactionThread(transaction));
        }
        threads.shutdown();
        if (!threads.awaitTermination(2, TimeUnit.MINUTES)) {
            System.err.println("ERROR: Timed out!");
            System.exit(1);
        }
        long end = System.nanoTime();
        storeRecord(condition, end - start);
    }

    /**
     * calls addPendingTransactions() using a list of transactions that correspond to the event
     * inside condition.
     */
    private void recordAddPendingTransactions(BenchmarkCondition condition) throws InterruptedException {
        ExecutorService threads = Executors.newFixedThreadPool(DEFAULT_NUM_THREADS);
        List<AionTransaction> transactions = getTransactionsForEvent(condition.event);
        long start = System.nanoTime();
        for (int i = 0; i < DEFAULT_NUM_REQUESTS; i++) {
            threads.execute(new AddPendingTransactionsThread(transactions));
        }
        threads.shutdown();
        if (!threads.awaitTermination(2, TimeUnit.MINUTES)) {
            System.err.println("ERROR: Timed out!");
            System.exit(1);
        }
        long end = System.nanoTime();
        storeRecord(condition, end - start);
    }

    /**
     * calls seedProcess() using a list of transactions that correspond to the event inside
     * condition.
     */
    private void recordSeedProcess(BenchmarkCondition condition) {
        List<AionTransaction> transactions = getTransactionsForEvent(condition.event);
        long start = System.nanoTime();
        pendingState.seedProcess(transactions);
        long end = System.nanoTime();
        storeRecord(condition, end - start);
    }

    /**
     * calls addPendingStateImpl() using a transaction and transaction nonce that correspond to
     * the event inside condition.
     */
    private void recordAddPendingTransactionImpl(BenchmarkCondition condition) {
        AionTransaction transaction = getTransactionForEvent(condition.event);
        BigInteger transactionNonce = getNonceForEvent(condition.event, transaction.getTo());
        long start = System.nanoTime();
        pendingState.addPendingTransactionImpl(transaction, transactionNonce);
        long end = System.nanoTime();
        storeRecord(condition, end - start);
    }

    /**
     * calls findCommonAncestor() on two blocks whose ancestor relationship is specified by the
     * event inside condition.
     */
    private void recordFindCommonAncestor(BenchmarkCondition condition) {
        Pair<IAionBlock, IAionBlock> blocks = setupBlockchainForCommonAncestors(condition.event);
        IAionBlock block1 = blocks.getLeft();
        IAionBlock block2 = blocks.getRight();
        long start = System.nanoTime();
        pendingState.findCommonAncestor(block1, block2);
        long end = System.nanoTime();
        storeRecord(condition, end - start);
    }

    /**
     * calls processBest() on a blockchain according to the specifications in condition.
     */
    private void recordProcessBest(BenchmarkCondition condition) throws InterruptedException {
        ExecutorService threads = Executors.newFixedThreadPool(DEFAULT_NUM_THREADS);
        setupBlockchain(DEFAULT_CHAIN_HEIGHT);
        AionBlock nextBest = produceNextBestBlock();
        pendingState.txBuffer = new ArrayList<>();
        long start = System.nanoTime();
        for (int i = 0; i < DEFAULT_NUM_REQUESTS; i++) {
            threads.execute(new ProcessBestThread(nextBest));
        }
        threads.shutdown();
        if (!threads.awaitTermination(2, TimeUnit.MINUTES)) {
            System.err.println("ERROR: Timed out!");
            System.exit(1);
        }
        long end = System.nanoTime();
        pendingState.txBuffer = null;
        storeRecord(condition, end - start);
    }

    /**
     * calls flushCachePendingTx() using a PendingTxCache object according to the specifications in
     * condition.
     */
    private void recordFlushCachePendingTx(BenchmarkCondition condition) {
        setupPendingTxCache(getPendingCacheSizeForEvent(condition.event));
        long start = System.nanoTime();
        pendingState.flushCachePendingTx();
        long end = System.nanoTime();
        storeRecord(condition, end - start);
    }

    /**
     * calls clearOutdated() using an ITxPool object that has an outdated transaction list of a size
     * specified by the event inside condition.
     */
    private void recordClearOutdated(BenchmarkCondition condition) {
        setupOutdatedTransactions(getNumOutdatedTxsForEvent(condition.event));
        long start = System.nanoTime();
        // block number actually has no time complexity affect; it is used for logging in the method.
        pendingState.clearOutdated(0);
        long end = System.nanoTime();
        storeRecord(condition, end - start);
    }

    /**
     * calls DumpPool() on an ITxPool set up for a call to its snapshot method according to the
     * specifications in condition.
     */
    private void recordDumpPool(BenchmarkCondition condition) throws InterruptedException {
        ExecutorService threads = Executors.newFixedThreadPool(FEW_THREADS);
        setupTransactionPoolForSnapshot(getTxPoolSnapshotSize(condition.event));
        long start = System.nanoTime();
        for (int i = 0; i < NUM_DUMP_POOL_REQUESTS; i++) {
            threads.execute(new DumpPoolThread());
        }
        threads.shutdown();
        if (!threads.awaitTermination(2, TimeUnit.MINUTES)) {
            System.err.println("ERROR: Timed out!");
            System.exit(1);
        }
        long end = System.nanoTime();
        storeRecord(condition, end - start);
    }

    /**
     * calls getPeersBestBlk13() on a P2pMgr set to have a number of peers as specified by
     * condition.
     */
    private void recordGetPeersBestBlk13(BenchmarkCondition condition) {
        setupP2pManager(getNumPeersForEvent(condition.event));
        long start = System.nanoTime();
        pendingState.getPeersBestBlk13();
        long end = System.nanoTime();
        storeRecord(condition, end - start);
    }

    private void recordRecoverCache(BenchmarkCondition condition) {
        //TODO
    }

    private void recordRecoverPool(BenchmarkCondition condition) {
        //TODO
    }

    //<----------------------------GRUNT WORK HELPER METHODS--------------------------------------->

    /**
     * Sets the appropriate fields/objects such that the code paths specified by paths will be
     * activated.
     */
    private void setUpCodePath(Set<CodePath> paths) {
        // ensure previous rounds do not interfere with this round by resetting.
        CfgAion.inst().getConsensus().seed = false;
        CfgAion.inst().getTx().buffer = false;
        CfgAion.inst().getTx().poolBackup = false;

        // enable only what we want to be set for this round.
        for (CodePath path : paths) {
            switch (path) {
                case IS_SEED:
                    CfgAion.inst().getConsensus().seed = true;
                    break;
                case BUFFER_ENABLED:
                    CfgAion.inst().getTx().buffer = true;
                    break;
                case IS_BACKUP:
                    CfgAion.inst().getTx().poolBackup = true;
                    break;
            }
        }
    }

    /**
     * Returns a list of AionTransaction objects corresponding to the Event event if event is an
     * event that makes use of AionTransaction objects in bulk. Otherwise the returned list has
     * undefined behaviour.
     */
    private List<AionTransaction> getTransactionsForEvent(Event event) {
        int numTransactions = getNumTransactionsForEvent(event);
        List<AionTransaction> transactions = new ArrayList<>(numTransactions);
        if (isMixedEvent(event)) {
            for (int i = 0; i < numTransactions; i++) {
                int index = RandomUtils.nextInt(0, singleTransactionEvents.size());
                transactions.add(getTransactionForEvent(singleTransactionEvents.get(index)));
            }
        } else {
            Event singleTxEvent = txsEventToTxEvent(event);
            for (int i = 0; i < numTransactions; i++) {
                transactions.add(getTransactionForEvent(singleTxEvent));
            }
        }
        return transactions;
    }

    /**
     * Returns an AionTransaction corresponding to the Event event if event is an event that makes
     * use of AionTransaction objects. Otherwise returns null.
     */
    private AionTransaction getTransactionForEvent(Event event) {
        AionTransaction transaction;
        ECKey key = ECKeyFac.inst().create();
        BigInteger nonce = BigInteger.ZERO;
        BigInteger value = BigInteger.ZERO;
        long nrg = 1_000_000;
        long nrgPrice = 1;

        switch (event) {
            case DUMP_POOL_AVG:
            case DUMP_POOL_LARGE:
            case CLEAR_OUTDATED_AVG:
            case CLEAR_OUTDATED_LARGE:
            case FLUSH_CACHE_AVG:
            case FLUSH_CACHE_LARGE:
            case ADD_PENDING_IMPL_DIFF_NONCE:
            case ADD_PENDING_IMPL_AVG_DATA:
            case ADD_TX_AVG_DATA: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                value.toByteArray(),
                RandomUtils.nextBytes(AVG_DATA_SIZE),
                nrg,
                nrgPrice);
                break;
            case ADD_PENDING_IMPL_LARGE_DATA:
            case ADD_TX_LARGE_DATA: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                value.toByteArray(),
                RandomUtils.nextBytes(LARGE_DATA_SIZE),
                nrg,
                nrgPrice);
                break;
            case ADD_PENDING_IMPL_LARGE_NONCE:
            case ADD_TX_LARGE_NONCE: transaction = new AionTransaction(
                LARGE_BIG_INT.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                value.toByteArray(),
                RandomUtils.nextBytes(AVG_DATA_SIZE),
                nrg,
                nrgPrice);
                break;
            case ADD_PENDING_IMPL_LARGE_NRG:
            case ADD_TX_LARGE_NRG: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                value.toByteArray(),
                RandomUtils.nextBytes(AVG_DATA_SIZE),
                Long.MAX_VALUE,
                nrgPrice);
                break;
            case ADD_PENDING_IMPL_LARGE_VALUE:
            case ADD_TX_LARGE_VALUE: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                LARGE_BIG_INT.toByteArray(),
                RandomUtils.nextBytes(AVG_DATA_SIZE),
                nrg,
                nrgPrice);
                break;
            case ADD_PENDING_IMPL_NULL_TO:
            case ADD_TX_NULL_TO: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                null,
                value.toByteArray(),
                RandomUtils.nextBytes(AVG_DATA_SIZE),
                nrg,
                nrgPrice);
                break;
            default: return null;
        }

        transaction.sign(key);
        return transaction;
    }

    /**
     * Returns the number of transactions to use for a call represented by Event event if event is
     * a transaction-dependent event -- otherwise this is meaningless.
     */
    private int getNumTransactionsForEvent(Event event) {
        if (event == Event.ADD_FEW_TXS_AVG_DATA ||
                event == Event.ADD_FEW_TXS_LARGE_DATA ||
                event == Event.ADD_FEW_TXS_LARGE_NONCES ||
                event == Event.ADD_FEW_TXS_LARGE_NRGS ||
                event == Event.ADD_FEW_TXS_LARGE_VALUES ||
                event == Event.ADD_FEW_TXS_NULL_TOS ||
                event == Event.ADD_FEW_TXS_MIXED) {
            return FEW_TXS;
        } else {
            return MANY_TXS;
        }
    }

    /**
     * Returns the number of threads to use for a call represented by Event event if event is a
     * thread-dependent event -- otherwise this is meaningless.
     */
    private int getNumThreadsForEvent(Event event) {
        if (event == Event.GET_REPO_FEW_FEW ||
                event == Event.GET_REPO_FEW_AVG ||
                event == Event.GET_REPO_FEW_MANY) {
            return FEW_THREADS;
        } else {
            return MANY_THREADS;
        }
    }

    /**
     * Returns the size of the PendingTxCache object for the Event event.
     */
    private int getPendingCacheSizeForEvent(Event event) {
        return (event == Event.FLUSH_CACHE_AVG) ? PENDING_TX_AVG_SIZE : PENDING_TX_LARGE_SIZE;
    }

    /**
     * Returns the number of calls to make for a call represented by Event event if event is a
     * thread-dependent event -- otherwise this is meaningless.
     */
    private int getNumRequestsForEvent(Event event) {
        if (event == Event.GET_REPO_FEW_FEW || event == Event.GET_REPO_MANY_FEW) {
            return FEW_REQUESTS;
        } else if (event == Event.GET_REPO_FEW_AVG || event == Event.GET_REPO_MANY_AVG) {
            return AVG_REQUESTS;
        } else {
            return MANY_REQUESTS;
        }
    }

    /**
     * Returns a nonce for the Event event, which will be the same as the recipient's nonce unless
     * the event specifies otherwise.
     * Returns a zero nonce if recipient is null.
     */
    private BigInteger getNonceForEvent(Event event, Address recipient) {
        if (recipient == null) {
            return BigInteger.ZERO;
        }
        BigInteger recipientNonce = pendingState.getRepository().getNonce(recipient);
        return (event == Event.ADD_PENDING_IMPL_DIFF_NONCE) ?
            recipientNonce.add(BigInteger.ONE) :
            recipientNonce;
    }

    /**
     * Returns the number of outdated transactions to put in the ITxPool object for the specified
     * event.
     */
    private int getNumOutdatedTxsForEvent(Event event) {
        return (event == Event.CLEAR_OUTDATED_AVG) ? OUTDATED_AVG_SIZE : OUTDATED_LARGE_SIZE;
    }

    /**
     * Returns the size of the accountView field that is used by the snapshot call for the Event
     * event.
     */
    private int getTxPoolSnapshotSize(Event event) {
        return (event == Event.DUMP_POOL_AVG) ? DUMP_POOL_AVG_SIZE : DUMP_POOL_LARGE_SIZE;
    }

    /**
     * Returns the number of peers to set in the P2pMgr object.
     */
    private int getNumPeersForEvent(Event event) {
        return (event == Event.GET_PEERS_BEST_FEW) ? FEW_PEERS : MANY_PEERS;
    }

    /**
     * Constructs a new P2pMgr object that has numPeers peers (active nodes) and assigns it to the
     * AionPendingStateImpl's p2pMgr field.
     */
    private void setupP2pManager(int numPeers) {
        P2pMgr p2pManager = newEmptyP2pManager(numPeers);
        p2pManager.nodeMgr = newNodeManagerWithActiveNodes(p2pManager, numPeers);
        pendingState.p2pMgr = p2pManager;
    }

    /**
     * Constructs a new NodeMgr object that has numActiveNodes active nodes and holds a reference
     * to the p2pMgr p2pManager.
     */
    private NodeMgr newNodeManagerWithActiveNodes(P2pMgr p2pManager, int numActiveNodes) {
        Logger logger = AionLoggerFactory.getLogger(LogEnum.VM.toString());
        NodeMgr nodeManager = new NodeMgr(p2pManager, numActiveNodes, 1, logger);
        for (int i = 0; i < numActiveNodes; i++) {
            Node node = new Node("127.0.0.1", 40_000);
            nodeManager.activeNodes.put(i, node);
        }
        return nodeManager;
    }

    /**
     * Returns a new P2pMgr object that can have numActiveNodes active nodes but does not yet have
     * any.
     */
    private P2pMgr newEmptyP2pManager(int numActiveNodes) {
        String[] bootNodes = new String[1];
        bootNodes[0] = "p2p://" + UUID.randomUUID().toString() + "@127.0.0.1:40000";

        return new P2pMgr(
            0,
            "",
            "0",
            "127.0.0.0",
            40_000,
            bootNodes,
            false,
            1,
            numActiveNodes,
            false,
            1);
    }

    /**
     * Constructs a new ITxPool object that contains an accountView field of size size. This is the
     * field used mostly by the snapshot call.
     */
    private void setupTransactionPoolForSnapshot(int size) {
        TxPoolA0<AionTransaction> txPool = new TxPoolA0<>();

        for (int i = 0; i < size; i++) {
            // using any dump pool event below will have the same effect..
            Address account = new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN));
            txPool.accountView.put(account, createNewAccountState(ACC_STATE_MAP_SIZE));
            txPool.add(getTransactionForEvent(Event.DUMP_POOL_AVG));
        }

        pendingState.txPool = txPool;
    }

    /**
     * Constructs a new AccountState object that contains a mapping of size mapSize.
     */
    private AccountState createNewAccountState(int mapSize) {
        Map<BigInteger, SimpleEntry<ByteArrayWrapper, BigInteger>> map = new HashMap<>();

        for (int i = 0; i < mapSize; i++) {
            ByteArrayWrapper wrapper = ByteArrayWrapper.wrap(RandomUtils.nextBytes(WRAPPER_SIZE));
            map.put(BigInteger.ZERO, new SimpleEntry<>(wrapper, BigInteger.ZERO));
        }

        AccountState accountState = new AccountState();
        accountState.updateMap(map);
        return accountState;
    }

    /**
     * Constructs an ITxPool instance that will hold numOutdated outdated transactions and assigns
     * it to the AionPendingStateImpl's txPool field.
     */
    private void setupOutdatedTransactions(int numOutdated) {
        ITxPool<AionTransaction> txPool = new TxPoolA0<>();
        for (int i = 0; i < numOutdated; i++) {
            // Any clear outdated event will have the same effect here..
            txPool.add(getTransactionForEvent(Event.CLEAR_OUTDATED_AVG));
        }
        pendingState.txPool = txPool;
    }

    /**
     * Constructs a PendingTxCache object whose size is cacheSize and assigns it to the
     * AionPendingStateImpl's pendingTxCache field.
     */
    private void setupPendingTxCache(int cacheSize) {
        PendingTxCache pendingCache = new PendingTxCache();
        for (int i = 0; i < cacheSize; i++) {
            // Any flush event will do the same thing here..
            pendingCache.addCacheTx(getTransactionForEvent(Event.FLUSH_CACHE_AVG));
        }
        pendingState.pendingTxCache = pendingCache;
    }

    /**
     * Produces a new IAionBlock whose parent is the current best block in the
     * AionPendingStateImpl's blockchain.
     */
    private AionBlock produceNextBestBlock() {
        return pendingState.blockchain.createNewBlock(
            pendingState.blockchain.getBestBlock(),
            null,                     //TODO: is this okay? Should we actually add txs?
            false);
    }

    /**
     * Constructs a new empty blockchain and assigns it to the AionPendingStateImpl's blockchain
     * field.
     */
    private void setupEmptyBlockchain() {
        StandaloneBlockchain.Bundle bundle = new Builder()
            .withValidatorConfiguration("simple")
            .withDefaultAccounts()
            .build();
        pendingState.blockchain = bundle.bc;
    }

    /**
     * Constructs a blockchain whose height is height and assigns it to the AionPendingStateImpl's
     * blockchain field.
     */
    private void setupBlockchain(int height) {
        // Make the blockchain the desired height.
        for (int currentHeight = 0; currentHeight < height; currentHeight++) {
            AionBlock block = pendingState.blockchain.createNewBlock(
                pendingState.blockchain.getBestBlock(),
                null,                   //TODO: is this okay? Should we actually add txs?
                false);
            pendingState.blockchain.add(block);
        }
    }

    /**
     * Returns a pairing of the two blocks that are to be used for the findCommonAncestor() call.
     * The distince to the ancestor is specified by event.
     */
    private Pair<IAionBlock, IAionBlock> setupBlockchainForCommonAncestors(Event event) {
        Pair<Integer, Integer> blockHeights = getBlockHeightsForCommonAncestors(event);
        int height1 = blockHeights.getLeft();
        int height2 = blockHeights.getRight();
        int blockchainHeight = getBlockchainHeight(event, Math.max(height1, height2));

        setupBlockchain(blockchainHeight);
        IAionBlock block1 = pendingState.blockchain.getBlockByNumber(height1);
        IAionBlock block2 = pendingState.blockchain.getBlockByNumber(height2);
        return Pair.of(block1, block2);
    }

    /**
     * Returns a pair of integers that stand for the heights of the two chains on the blockchain
     * for the findCommonAncestor() call as specified by the Event event.
     */
    private Pair<Integer, Integer> getBlockHeightsForCommonAncestors(Event event) {
        switch (event) {
            case FIND_ANCESTOR_AVG_DIST_EQUIDISTANT_AT_TOP:
            case FIND_ANCESTOR_AVG_DIST_EQUIDISTANT_NOT_TOP:
                return Pair.of(AVG_BLOCK_HEIGHT, AVG_BLOCK_HEIGHT);
            case FIND_ANCESTOR_AVG_DIST_NONEQUIDISTANT_AT_TOP:
            case FIND_ANCESTOR_AVG_DIST_NONEQUIDISTANT_NOT_TOP:
                Integer diff = AVG_BLOCK_HEIGHT / 10;
                return Pair.of(diff, AVG_BLOCK_HEIGHT + diff);
            case FIND_ANCESTOR_FAR_DIST_EQUIDISTANT_AT_TOP:
            case FIND_ANCESTOR_FAR_DIST_EQUIDISTANT_NOT_TOP:
                return Pair.of(FAR_BLOCK_HEIGHT, FAR_BLOCK_HEIGHT);
            case FIND_ANCESTOR_FAR_DIST_NONEQUIDISTANT_AT_TOP:
            case FIND_ANCESTOR_FAR_DIST_NONEQUIDISTANT_NOT_TOP:
                diff = FAR_BLOCK_HEIGHT / 10;
                return Pair.of(diff, FAR_BLOCK_HEIGHT + diff);
            default: return null;
        }
    }

    /**
     * Returns the total height of the full blockchain for the findCommonAncestor() Event event
     * given that the highest of the two query blocks' height is given by maxBlockHeight.
     */
    private int getBlockchainHeight(Event event, int maxBlockHeight) {
        return (maxBlockIsAtTop(event)) ? maxBlockHeight : maxBlockHeight + CHAIN_EXTRA_HEIGHT;
    }

    /**
     * Returns true only if the Event event for the findCommonAncestor() call specifies that the
     * highest of the two query blocks (the max block) is at the top of the blockchain (is the best
     * block).
     */
    private boolean maxBlockIsAtTop(Event event) {
        return event == Event.FIND_ANCESTOR_AVG_DIST_EQUIDISTANT_AT_TOP ||
            event == Event.FIND_ANCESTOR_AVG_DIST_NONEQUIDISTANT_AT_TOP ||
            event == Event.FIND_ANCESTOR_FAR_DIST_EQUIDISTANT_AT_TOP ||
            event == Event.FIND_ANCESTOR_FAR_DIST_NONEQUIDISTANT_AT_TOP;
    }

    /**
     * Adds a new record for time to the specified condition in the records field.
     */
    private void storeRecord(BenchmarkCondition condition, long time) {
        List<Long> times = records.get(condition);
        if (times == null) {
            List<Long> newTimes = new ArrayList<>();
            newTimes.add(time);
            records.put(condition, newTimes);
        } else {
           times.add(time);
        }
    }

    /**
     * Returns true only if event is a mixed transaction event.
     */
    private boolean isMixedEvent(Event event) {
        return event == Event.ADD_FEW_TXS_MIXED ||
            event == Event.ADD_MANY_TXS_MIXED ||
            event == Event.SEED_PROCESS_FEW_TXS_MIXED ||
            event == Event.SEED_PROCESS_MANY_TXS_MIXED;
    }

    /**
     * Expects an event that calls getPendingTransactions() and converts this event to its
     * corresponding event that calls getPendingTransaction().
     */
    private Event txsEventToTxEvent(Event event) {
        switch (event) {
            case SEED_PROCESS_FEW_TXS_AVG_DATA:
            case SEED_PROCESS_MANY_TXS_AVG_DATA:
            case ADD_FEW_TXS_AVG_DATA:
            case ADD_MANY_TXS_AVG_DATA: return Event.ADD_TX_AVG_DATA;
            case SEED_PROCESS_FEW_TXS_LARGE_DATA:
            case SEED_PROCESS_MANY_TXS_LARGE_DATA:
            case ADD_FEW_TXS_LARGE_DATA:
            case ADD_MANY_TXS_LARGE_DATA: return Event.ADD_TX_LARGE_DATA;
            case SEED_PROCESS_FEW_TXS_LARGE_NONCES:
            case SEED_PROCESS_MANY_TXS_LARGE_NONCES:
            case ADD_FEW_TXS_LARGE_NONCES:
            case ADD_MANY_TXS_LARGE_NONCES: return Event.ADD_TX_LARGE_NONCE;
            case SEED_PROCESS_FEW_TXS_LARGE_NRGS:
            case SEED_PROCESS_MANY_TXS_LARGE_NRGS:
            case ADD_FEW_TXS_LARGE_NRGS:
            case ADD_MANY_TXS_LARGE_NRGS: return Event.ADD_TX_LARGE_NRG;
            case SEED_PROCESS_FEW_TXS_LARGE_VALUES:
            case SEED_PROCESS_MANY_TXS_LARGE_VALUES:
            case ADD_FEW_TXS_LARGE_VALUES:
            case ADD_MANY_TXS_LARGE_VALUES: return Event.ADD_TX_LARGE_VALUE;
            case SEED_PROCESS_FEW_TXS_NULL_TOS:
            case SEED_PROCESS_MANY_TXS_NULL_TOS:
            case ADD_FEW_TXS_NULL_TOS:
            case ADD_MANY_TXS_NULL_TOS: return Event.ADD_TX_NULL_TO;
            default: return null;
        }
    }

    /**
     * Makes the appropriate call corresponding to the specified BenchmarkCondition, which
     * specifies the event and its code paths.
     */
    private void makeCall(BenchmarkCondition condition) throws InterruptedException {
        setUpCodePath(condition.path);
        switch (condition.event) {
            case INST: recordInst(condition);
                break;
            case GET_REPO_FEW_FEW:
            case GET_REPO_FEW_AVG:
            case GET_REPO_FEW_MANY:
            case GET_REPO_MANY_FEW:
            case GET_REPO_MANY_AVG:
            case GET_REPO_MANY_MANY: recordGetRepository(condition);
                break;
            case GET_PENDING_TX: recordGetPendingTransactions(condition);
                break;
            case ADD_TX_AVG_DATA:
            case ADD_TX_LARGE_DATA:
            case ADD_TX_LARGE_NONCE:
            case ADD_TX_LARGE_NRG:
            case ADD_TX_LARGE_VALUE:
            case ADD_TX_NULL_TO: recordAddPendingTransaction(condition);
                break;
            case ADD_FEW_TXS_AVG_DATA:
            case ADD_FEW_TXS_LARGE_DATA:
            case ADD_FEW_TXS_LARGE_NONCES:
            case ADD_FEW_TXS_LARGE_NRGS:
            case ADD_FEW_TXS_LARGE_VALUES:
            case ADD_FEW_TXS_MIXED:
            case ADD_FEW_TXS_NULL_TOS:
            case ADD_MANY_TXS_AVG_DATA:
            case ADD_MANY_TXS_LARGE_DATA:
            case ADD_MANY_TXS_LARGE_NONCES:
            case ADD_MANY_TXS_LARGE_NRGS:
            case ADD_MANY_TXS_LARGE_VALUES:
            case ADD_MANY_TXS_MIXED:
            case ADD_MANY_TXS_NULL_TOS: recordAddPendingTransactions(condition);
                break;
            case SEED_PROCESS_FEW_TXS_AVG_DATA:
            case SEED_PROCESS_FEW_TXS_LARGE_DATA:
            case SEED_PROCESS_FEW_TXS_LARGE_NONCES:
            case SEED_PROCESS_FEW_TXS_LARGE_NRGS:
            case SEED_PROCESS_FEW_TXS_LARGE_VALUES:
            case SEED_PROCESS_FEW_TXS_MIXED:
            case SEED_PROCESS_FEW_TXS_NULL_TOS:
            case SEED_PROCESS_MANY_TXS_AVG_DATA:
            case SEED_PROCESS_MANY_TXS_LARGE_DATA:
            case SEED_PROCESS_MANY_TXS_LARGE_NONCES:
            case SEED_PROCESS_MANY_TXS_LARGE_NRGS:
            case SEED_PROCESS_MANY_TXS_LARGE_VALUES:
            case SEED_PROCESS_MANY_TXS_MIXED:
            case SEED_PROCESS_MANY_TXS_NULL_TOS: recordSeedProcess(condition);
                break;
            case ADD_PENDING_IMPL_AVG_DATA:
            case ADD_PENDING_IMPL_LARGE_DATA:
            case ADD_PENDING_IMPL_LARGE_NONCE:
            case ADD_PENDING_IMPL_LARGE_NRG:
            case ADD_PENDING_IMPL_LARGE_VALUE:
            case ADD_PENDING_IMPL_NULL_TO:
            case ADD_PENDING_IMPL_DIFF_NONCE: recordAddPendingTransactionImpl(condition);
                break;
            case FIND_ANCESTOR_AVG_DIST_EQUIDISTANT_AT_TOP:
            case FIND_ANCESTOR_AVG_DIST_EQUIDISTANT_NOT_TOP:
            case FIND_ANCESTOR_AVG_DIST_NONEQUIDISTANT_AT_TOP:
            case FIND_ANCESTOR_AVG_DIST_NONEQUIDISTANT_NOT_TOP:
            case FIND_ANCESTOR_FAR_DIST_EQUIDISTANT_AT_TOP:
            case FIND_ANCESTOR_FAR_DIST_EQUIDISTANT_NOT_TOP:
            case FIND_ANCESTOR_FAR_DIST_NONEQUIDISTANT_AT_TOP:
            case FIND_ANCESTOR_FAR_DIST_NONEQUIDISTANT_NOT_TOP: recordFindCommonAncestor(condition);
                break;
            case PROCESS_BEST_AVG_CHAIN:
            case PROCESS_BEST_LONG_CHAIN: recordProcessBest(condition);
                break;
            case FLUSH_CACHE_AVG:
            case FLUSH_CACHE_LARGE: recordFlushCachePendingTx(condition);
                break;
            case CLEAR_OUTDATED_AVG:
            case CLEAR_OUTDATED_LARGE: recordClearOutdated(condition);
                break;
            case DUMP_POOL_AVG:
            case DUMP_POOL_LARGE: recordDumpPool(condition);
                break;
            case GET_PEERS_BEST_FEW:
            case GET_PEERS_BEST_MANY: recordGetPeersBestBlk13(condition);
                break;
        }
    }

    //<-------------------------METHODS THAT PRODUCE EVENT ORDERINGS------------------------------->

    /**
     * Produces a custom built ordering of each call event and assigns it to the orderOfCalls field.
     */
    private void getCustomCallOrder() {
        orderOfCalls = new CallBuilder()
            .add(new BenchmarkCondition(Event.DUMP_POOL_AVG))
            .build();
    }

    /**
     * Produces a randomized ordering of each possible event (and each relevant code path
     * combination) and assigns it to the orderOfCalls field.
     */
    private void getRandomCallOrder() {
        List<Event> events = new ArrayList<>(Arrays.asList(Event.values()));
        List<BenchmarkCondition> ordering = new ArrayList<>();
        for (Event event : events) {
            ordering.add(new BenchmarkCondition(event));
        }
        Collections.shuffle(ordering);
        orderOfCalls = ordering;
    }

    //<----------------------------------HELPERS FOR DISPLAYING------------------------------------>

    /**
     * Displays the maximum duration in durations.
     */
    private void printMaxDuration(List<Long> durations) {
        long maxDuration = durations.stream().mapToLong(l -> l).max().getAsLong();
        System.out.printf(
            "\n\t\tMax duration: %,d milliseconds (%,d nanoseconds)",
            TimeUnit.NANOSECONDS.toMillis(maxDuration),
            maxDuration);
    }

    /**
     * Displays the minimum duration in durations.
     */
    private void printMinDuration(List<Long> durations) {
        long minDuration = durations.stream().mapToLong(l -> l).min().getAsLong();
        System.out.printf(
            "\n\t\tMin duration: %,d milliseconds (%,d nanoseconds)",
            TimeUnit.NANOSECONDS.toMillis(minDuration),
            minDuration);
    }

    /**
     * Displays the average duration in durations.
     */
    private void printAverageDuration(List<Long> durations) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Long time : durations) {
            sum = sum.add(BigDecimal.valueOf(time));
        }
        BigDecimal average = sum.divide(BigDecimal.valueOf(durations.size()), RoundingMode.HALF_EVEN);
        BigDecimal millisAvg = average.divide(BigDecimal.valueOf(1_000_000));
        System.out.printf(
            "\n\t\tAverage duration: %s milliseconds (%s nanoseconds)",
            String.format("%,.2f", millisAvg),
            String.format("%,.2f", average));
    }

    /**
     * Returns a string representation of durations for displaying.
     */
    private String durationsToString(List<Long> durations) {
        StringBuilder builder = new StringBuilder("[ ");

        int count = 1;
        for (Long duration : durations) {
            builder
                .append(String.format("%,d", TimeUnit.NANOSECONDS.toMillis(duration)))
                .append(" milliseconds (")
                .append(String.format("%,d", duration))
                .append(" nanoseconds) ");
            if (count < durations.size()) {
                builder.append(", ");
            }
            if ((count % 5 == 0) && (count < durations.size())) {
                builder.append("\n\t\t");
            }
            count++;
        }

        return builder.append("]").toString();
    }

    /**
     * Returns a string representation of event for displaying.
     */
    private String eventToString(Event event) {
        switch (event) {
            case INST:
                return "inst()";
            case GET_REPO_FEW_FEW:
                return "getRepository() with few threads and few calls";
            case GET_REPO_FEW_AVG:
                return "getRepository() with few threads and avg calls";
            case GET_REPO_FEW_MANY:
                return "getRepository() with few threads and many calls";
            case GET_REPO_MANY_FEW:
                return "getRepository() with many threads and few calls";
            case GET_REPO_MANY_AVG:
                return "getRepository() with many threads and avg calls";
            case GET_REPO_MANY_MANY:
                return "getRepository() with many threads and many calls";
            case GET_PENDING_TX:
                return "getPendingTransactions()";
            case ADD_TX_AVG_DATA:
                return "addPendingTransaction() with average-sized data";
            case ADD_TX_LARGE_DATA:
                return "addPendingTransaction() with large-sized data";
            case ADD_TX_LARGE_NONCE:
                return "addPendingTransaction() with a large nonce";
            case ADD_TX_LARGE_VALUE:
                return "addPendingTransaction() with a large value";
            case ADD_TX_LARGE_NRG:
                return "addPendingTransaction() with a large energy limit";
            case ADD_TX_NULL_TO:
                return "addPendingTransaction() with a null recipient";
            case ADD_FEW_TXS_AVG_DATA:
                return "addPendingTransactions() with a few transactions with average-sized data";
            case ADD_FEW_TXS_LARGE_DATA:
                return "addPendingTransactions() with a few transactions with large-sized data";
            case ADD_FEW_TXS_LARGE_NONCES:
                return "addPendingTransactions() with a few transactions with large nonces";
            case ADD_FEW_TXS_LARGE_NRGS:
                return "addPendingTransactions() with a few transactions with large energy limits";
            case ADD_FEW_TXS_LARGE_VALUES:
                return "addPendingTransactions() with a few transactions with large values";
            case ADD_FEW_TXS_MIXED:
                return "addPendingTransactions() with a few mixed transactions";
            case ADD_FEW_TXS_NULL_TOS:
                return "addPendingTransactions() with a few transactions with null recipients";
            case ADD_MANY_TXS_AVG_DATA:
                return "addPendingTransactions() with many transactions with average-sized data";
            case ADD_MANY_TXS_LARGE_DATA:
                return "addPendingTransactions() with many transactions with large-sized data";
            case ADD_MANY_TXS_LARGE_NONCES:
                return "addPendingTransactions() with many transactions with large nonces";
            case ADD_MANY_TXS_LARGE_NRGS:
                return "addPendingTransactions() with many transactions with large energy limits";
            case ADD_MANY_TXS_LARGE_VALUES:
                return "addPendingTransactions() with many transactions with large values";
            case ADD_MANY_TXS_MIXED:
                return "addPendingTransactions() with many mixed transactions";
            case ADD_MANY_TXS_NULL_TOS:
                return "addPendingTransactions() with many transactions with null recipients";
            case SEED_PROCESS_FEW_TXS_AVG_DATA:
                return "seedProcess() with a few transactions with average-sized data";
            case SEED_PROCESS_FEW_TXS_LARGE_DATA:
                return "seedProcess() with a few transactions with large-sized data";
            case SEED_PROCESS_FEW_TXS_LARGE_NONCES:
                return "seedProcess() with a few transactions with large nonces";
            case SEED_PROCESS_FEW_TXS_LARGE_NRGS:
                return "seedProcess() with a few transactions with large energy limits";
            case SEED_PROCESS_FEW_TXS_LARGE_VALUES:
                return "seedProcess() with a few transactions with large values";
            case SEED_PROCESS_FEW_TXS_MIXED:
                return "seedProcess() with a few mixed transactions";
            case SEED_PROCESS_FEW_TXS_NULL_TOS:
                return "seedProcess() with a few transactions with null recipients";
            case SEED_PROCESS_MANY_TXS_AVG_DATA:
                return "seedProcess() with many transactions with average-sized data";
            case SEED_PROCESS_MANY_TXS_LARGE_DATA:
                return "seedProcess() with many transactions with large-sized data";
            case SEED_PROCESS_MANY_TXS_LARGE_NONCES:
                return "seedProcess() with many transactions with large nonces";
            case SEED_PROCESS_MANY_TXS_LARGE_NRGS:
                return "seedProcess() with many transactions with large energy limits";
            case SEED_PROCESS_MANY_TXS_LARGE_VALUES:
                return "seedProcess() with many transactions with large values";
            case SEED_PROCESS_MANY_TXS_MIXED:
                return "seedProcess() with many mixed transactions";
            case SEED_PROCESS_MANY_TXS_NULL_TOS:
                return "seedProcess() with many transactions with null recipients";
            case ADD_PENDING_IMPL_AVG_DATA:
                return "addPendingStateImpl() with average-sized data";
            case ADD_PENDING_IMPL_LARGE_DATA:
                return "addPendingStateImpl() with large-sized data";
            case ADD_PENDING_IMPL_LARGE_NONCE:
                return "addPendingStateImpl() with large nonce";
            case ADD_PENDING_IMPL_LARGE_NRG:
                return "addPendingStateImpl() with large energy limit";
            case ADD_PENDING_IMPL_LARGE_VALUE:
                return "addPendingStateImpl() with large value";
            case ADD_PENDING_IMPL_NULL_TO:
                return "addPendingStateImpl() with null recipient";
            case ADD_PENDING_IMPL_DIFF_NONCE:
                return "addPendingStateImpl() with a different transaction nonce than the recipient";
            case FIND_ANCESTOR_AVG_DIST_EQUIDISTANT_AT_TOP:
                return "findCommonAncestor() with equidistant at average distance at top";
            case FIND_ANCESTOR_AVG_DIST_EQUIDISTANT_NOT_TOP:
                return "findCommonAncestor() with equidistant at average distance NOT at top";
            case FIND_ANCESTOR_AVG_DIST_NONEQUIDISTANT_AT_TOP:
                return "findCommonAncestor() with non-equidistant blocks at average distance at top";
            case FIND_ANCESTOR_AVG_DIST_NONEQUIDISTANT_NOT_TOP:
                return "findCommonAncestor() with non-equidistant blocks at average distance NOT at top";
            case FIND_ANCESTOR_FAR_DIST_EQUIDISTANT_AT_TOP:
                return "findCommonAncestor() with equidistant blocks at far distance at top";
            case FIND_ANCESTOR_FAR_DIST_EQUIDISTANT_NOT_TOP:
                return "findCommonAncestor() with equidistant blocks at far distance NOT at top";
            case FIND_ANCESTOR_FAR_DIST_NONEQUIDISTANT_AT_TOP:
                return "findCommonAncestor() with non-equidistant blocks at far distance at top";
            case FIND_ANCESTOR_FAR_DIST_NONEQUIDISTANT_NOT_TOP:
                return "findCommonAncestor() with non-equidistant blocks at far distance NOT at top";
            case PROCESS_BEST_AVG_CHAIN:
                return "processBest() with an average-sized blockchain";
            case PROCESS_BEST_LONG_CHAIN:
                return "processBest() with a large-sized blockchain";
            case FLUSH_CACHE_AVG:
                return "flushCachePendingTx() with an average-sized transaction cache";
            case FLUSH_CACHE_LARGE:
                return "flushCachePendingTx() with a large-sized transaction cache";
            case CLEAR_OUTDATED_AVG:
                return "clearOutdated() with an average-sized outdated transaction pool";
            case CLEAR_OUTDATED_LARGE:
                return "clearOutdated() with a large-sized outdated transaction pool";
            case DUMP_POOL_AVG:
                return "DumpPool() with an average-sized accountView";
            case DUMP_POOL_LARGE:
                return "DumpPool() with a large-sized accountView";
            case GET_PEERS_BEST_FEW:
                return "getPeersBestBlk13() with few peers";
            case GET_PEERS_BEST_MANY:
                return "getPeersBestBlk13() with many peers";
            default: return "";
        }
    }

    /**
     * Returns a string representation for paths for displaying.
     */
    private String codePathToString(Set<CodePath> paths) {
        if ((paths == null) || (paths.isEmpty())) {
            return " default code paths";
        }

        StringBuilder builder = new StringBuilder();
        int count = 1;
        for (CodePath path : paths) {
            switch (path) {
                case IS_SEED: builder.append(" isSeed = True ");
                    break;
                case IS_BACKUP: builder.append(" poolBackUp = True ");
                    break;
                case BUFFER_ENABLED: builder.append(" bufferEnable = True ");
                    break;
                default: return "";
            }
            if (count < paths.size()) {
                builder.append(", ");
            }
            count++;
        }
        return builder.toString();
    }

    /**
     * Displays the records.
     */
    private void printRecords() {
        for (BenchmarkCondition condition : orderOfCalls) {
            List<Long> times = records.get(condition);
            System.out.print("\n\n" + condition);
            printMaxDuration(times);
            printMinDuration(times);
            printAverageDuration(times);
            System.out.printf("\n\t\t%s", durationsToString(times));
        }
    }

    //<------------------------------------HELPER CLASSES------------------------------------------>

    /**
     * Thread whose job is simply to call getRepository().
     */
    private class GetRepoThread implements Runnable {

        @Override
        public void run() {
            pendingState.getRepository();
        }
    }

    /**
     * Thread whose job is simply to call addPendingTransaction().
     */
    private class AddPendingTransactionThread implements Runnable {
        private AionTransaction transaction;

        AddPendingTransactionThread(AionTransaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public void run() {
            pendingState.addPendingTransaction(this.transaction);
        }
    }

    /**
     * Thread whose job is simply to call addPendingTransactions().
     */
    private class AddPendingTransactionsThread implements Runnable {
        private List<AionTransaction> transactions;

        AddPendingTransactionsThread(List<AionTransaction> transactions) {
            this.transactions = transactions;
        }

        @Override
        public void run() {
            pendingState.addPendingTransactions(this.transactions);
        }
    }

    /**
     * Thread whose job is simply to call processBestThread().
     */
    private class ProcessBestThread implements Runnable {
        private AionBlock block;

        ProcessBestThread(AionBlock block) {
            this.block = block;
        }

        @Override
        public void run() {
            // Using null receipts takes us down worst-case path & easiest to set up..
            pendingState.processBest(this.block, null);
        }
    }

    /**
     * Thread whose job is simply to call DumpPool().
     */
    private class DumpPoolThread implements Runnable {

        @Override
        public void run() {
            pendingState.DumpPool();
        }
    }

    /**
     * A class containing the conditions in which a particular benchmark call was called.
     */
    private class BenchmarkCondition {
        private final Event event;
        private final Set<CodePath> path;

        BenchmarkCondition(Event event, Set<CodePath> path) {
            this.event = event;
            this.path = path;
        }

        BenchmarkCondition(Event event) {
            this(event, new HashSet<>());
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) { return false; }
            if (!(other instanceof BenchmarkCondition)) { return false; }
            BenchmarkCondition otherBenchmarkCondition = (BenchmarkCondition) other;
            if (this.event != otherBenchmarkCondition.event) { return false; }
            return this.path.equals(otherBenchmarkCondition.path);
        }

        @Override
        public int hashCode() {
            return this.event.hashCode() + ((this.path == null) ? 0 : this.path.hashCode());
        }

        @Override
        public String toString() {
            return eventToString(this.event) + " using the code path: " + codePathToString(this.path);
        }
    }

    /**
     * A convenience class for building a list of calls to make.
     */
    private class CallBuilder {
        private List<BenchmarkCondition> calls;

        CallBuilder() {
            this.calls = new ArrayList<>();
        }

        CallBuilder add(BenchmarkCondition call) {
            this.calls.add(call);
            return this;
        }

        List<BenchmarkCondition> build() {
            return this.calls;
        }
    }

    /**
     * A convenience class for building a set of code paths to enforce.
     */
    private class CodePathBuilder {
        private Set<CodePath> paths;

        CodePathBuilder() {
            this.paths = new HashSet<>();
        }

        CodePathBuilder add(CodePath path) {
            this.paths.add(path);
            return this;
        }

        Set<CodePath> build() {
            return this.paths;
        }
    }

}
