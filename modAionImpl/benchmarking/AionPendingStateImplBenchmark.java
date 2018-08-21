import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.evtmgr.impl.mgr.EventMgrA0;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Class used to measure the performance of the AionPendingStateImpl class.
 *
 * @author Nick Nadeau
 */
public class AionPendingStateImplBenchmark {
    private static EnumMap<Event, Long> records;
    private static Map<Integer, Event> singleTransactionEvents;
    private AionPendingStateImpl pendingState;
    private List<Event> callOrder;

    private enum Event {
        INST, GET_REPO_FEW_FEW, GET_REPO_FEW_AVG, GET_REPO_FEW_MANY, GET_REPO_MANY_FEW,
        GET_REPO_MANY_AVG, GET_REPO_MANY_MANY, GET_PENDING_TX, ADD_TX_AVG_DATA,
        ADD_TX_LARGE_DATA, ADD_TX_LARGE_NONCE, ADD_TX_LARGE_VALUE, ADD_TX_LARGE_NRG, ADD_TX_NULL_TO,
        ADD_FEW_TXS_AVG_DATA, ADD_FEW_TXS_LARGE_DATA, ADD_FEW_TXS_LARGE_NONCES,
        ADD_FEW_TXS_LARGE_VALUES, ADD_FEW_TXS_LARGE_NRGS, ADD_FEW_TXS_NULL_TOS, ADD_FEW_TXS_MIXED,
        ADD_MANY_TXS_AVG_DATA, ADD_MANY_TXS_LARGE_DATA, ADD_MANY_TXS_LARGE_NONCES,
        ADD_MANY_TXS_LARGE_VALUES, ADD_MANY_TXS_LARGE_NRGS, ADD_MANY_TXS_NULL_TOS, ADD_MANY_TXS_MIXED
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

    @Before
    public void setup() {
        records = new EnumMap<>(Event.class);
        AionBlockchainImpl.inst().setEventManager(new EventMgrA0(new Properties())); // is this correct setup?
        doEvent(Event.INST);
    }

    @After
    public void tearDown() {
        callOrder = null;
    }

    @Test
    public void testRandomizedBenchmarking() {
        callOrder = getRandomCallOrder();
        for (Event event : callOrder) {
            doEvent(event);
        }
        printRecords();
    }

    //<----------------------------METHODS FOR PERFORMANCE RECORDING------------------------------->

    /**
     * Calls inst().
     */
    private void recordInst() {
        long start = System.nanoTime();
        pendingState = AionPendingStateImpl.inst();
        long end = System.nanoTime();
        records.put(Event.INST, end - start);
    }

    /**
     * Calls getRepository() a certain number of times by a certain number of threads - both of
     * which numbers are specified by the Event event.
     */
    private void recordGetRepository(Event event) {
        int numThreads = getNumThreadsForEvent(event);
        int numRequests = getNumRequestsForEvent(event);
        ExecutorService threads = Executors.newFixedThreadPool(numThreads);
        long start = System.nanoTime();
        for (int i = 0; i < numRequests; i++) {
            threads.execute(new GetRepoThread());
        }
        long end = System.nanoTime();
        threads.shutdown();
        records.put(event, end - start);
    }

    /**
     * Calls getPendingTransactions().
     */
    private void recordGetPendingTransactions() {
        long start = System.nanoTime();
        pendingState.getPendingTransactions();
        long end = System.nanoTime();
        records.put(Event.GET_PENDING_TX, end - start);
    }

    /**
     * Calls addPendingTransaction() using a transaction that corresponds to the Event event.
     */
    private void recordAddPendingTransaction(Event event) {
        AionTransaction transaction = getTransactionForEvent(event);
        long start = System.nanoTime();
        pendingState.addPendingTransaction(transaction);
        long end = System.nanoTime();
        records.put(event, end - start);
    }

    /**
     * calls addPendingTransactions() using a list of transactions that correspond to the Event
     * event.
     */
    private void recordAddPendingTransactions(Event event) {
        List<AionTransaction> transactions = getTransactionsForEvent(event);
        long start = System.nanoTime();
        pendingState.addPendingTransactions(transactions);
        long end = System.nanoTime();
        records.put(event, end - start);
    }

    //<----------------------------GRUNT WORK HELPER METHODS--------------------------------------->

    /**
     * Returns a list of AionTransaction objects corresponding to the Event event if event is an
     * event that makes use of AionTransaction objects in bulk. Otherwise the returned list has
     * undefined behaviour.
     */
    private List<AionTransaction> getTransactionsForEvent(Event event) {
        int numTransactions = getNumTransactionsForEvent(event);
        List<AionTransaction> transactions = new ArrayList<>(numTransactions);
        if (event == Event.ADD_FEW_TXS_MIXED || event == Event.ADD_MANY_TXS_MIXED) {
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
            case ADD_TX_AVG_DATA: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                value.toByteArray(),
                RandomUtils.nextBytes(2_500),
                nrg,
                nrgPrice);
                break;
            case ADD_TX_LARGE_DATA: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                value.toByteArray(),
                RandomUtils.nextBytes(50_000),
                nrg,
                nrgPrice);
                break;
            case ADD_TX_LARGE_NONCE: transaction = new AionTransaction(
                BigInteger.TWO.pow(1_000).toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                value.toByteArray(),
                RandomUtils.nextBytes(2_500),
                nrg,
                nrgPrice);
                break;
            case ADD_TX_LARGE_NRG: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                value.toByteArray(),
                RandomUtils.nextBytes(2_500),
                Long.MAX_VALUE,
                nrgPrice);
                break;
            case ADD_TX_LARGE_VALUE: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                BigInteger.TWO.pow(1_000).toByteArray(),
                RandomUtils.nextBytes(2_500),
                nrg,
                nrgPrice);
                break;
            case ADD_TX_NULL_TO: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                null,
                value.toByteArray(),
                RandomUtils.nextBytes(2_500),
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
            return 20;
        } else {
            return 1_500;
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
            return 5;
        } else {
            return 50;
        }
    }

    /**
     * Returns the number of calls to make for a call represented by Event event if event is a
     * thread-dependent event -- otherwise this is meaningless.
     */
    private int getNumRequestsForEvent(Event event) {
        if (event == Event.GET_REPO_FEW_FEW || event == Event.GET_REPO_MANY_FEW) {
            return 300;
        } else if (event == Event.GET_REPO_FEW_AVG || event == Event.GET_REPO_MANY_AVG) {
            return 10_000;
        } else {
            return 100_000;
        }
    }

    /**
     * Expects an event that calls getPendingTransactions() and converts this event to its
     * corresponding event that calls getPendingTransaction().
     */
    private Event txsEventToTxEvent(Event event) {
        switch (event) {
            case ADD_FEW_TXS_AVG_DATA:
            case ADD_MANY_TXS_AVG_DATA: return Event.ADD_TX_AVG_DATA;
            case ADD_FEW_TXS_LARGE_DATA:
            case ADD_MANY_TXS_LARGE_DATA: return Event.ADD_TX_LARGE_DATA;
            case ADD_FEW_TXS_LARGE_NONCES:
            case ADD_MANY_TXS_LARGE_NONCES: return Event.ADD_TX_LARGE_NONCE;
            case ADD_FEW_TXS_LARGE_NRGS:
            case ADD_MANY_TXS_LARGE_NRGS: return Event.ADD_TX_LARGE_NRG;
            case ADD_FEW_TXS_LARGE_VALUES:
            case ADD_MANY_TXS_LARGE_VALUES: return Event.ADD_TX_LARGE_VALUE;
            case ADD_FEW_TXS_NULL_TOS:
            case ADD_MANY_TXS_NULL_TOS: return Event.ADD_TX_NULL_TO;
            default: return null;
        }
    }

    /**
     * Makes the appropriate call corresponding to the Event event.
     */
    private void doEvent(Event event) {
        switch (event) {
            case INST: recordInst();
                break;
            case GET_REPO_FEW_FEW:
            case GET_REPO_FEW_AVG:
            case GET_REPO_FEW_MANY:
            case GET_REPO_MANY_FEW:
            case GET_REPO_MANY_AVG:
            case GET_REPO_MANY_MANY: recordGetRepository(event);
                break;
            case GET_PENDING_TX: recordGetPendingTransactions();
                break;
            case ADD_TX_AVG_DATA:
            case ADD_TX_LARGE_DATA:
            case ADD_TX_LARGE_NONCE:
            case ADD_TX_LARGE_NRG:
            case ADD_TX_LARGE_VALUE:
            case ADD_TX_NULL_TO: recordAddPendingTransaction(event);
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
            case ADD_MANY_TXS_NULL_TOS: recordAddPendingTransactions(event);
                break;
        }
    }

    /**
     * Returns a string representation of event for record displaying.
     */
    private String eventToString(Event event) {
        switch (event) {
            case INST: return "inst()";
            case GET_REPO_FEW_FEW: return "getRepository() with few threads and few calls";
            case GET_REPO_FEW_AVG: return "getRepository() with few threads and avg calls";
            case GET_REPO_FEW_MANY: return "getRepository() with few threads and many calls";
            case GET_REPO_MANY_FEW: return "getRepository() with many threads and few calls";
            case GET_REPO_MANY_AVG: return "getRepository() with many threads and avg calls";
            case GET_REPO_MANY_MANY: return "getRepository() with many threads and many calls";
            case GET_PENDING_TX: return "getPendingTransactions()";
            case ADD_TX_AVG_DATA: return "addPendingTransaction() with average-sized data";
            case ADD_TX_LARGE_DATA: return "addPendingTransaction() with large-sized data";
            case ADD_TX_LARGE_NONCE: return "addPendingTransaction() with a large nonce";
            case ADD_TX_LARGE_VALUE: return "addPendingTransaction() with a large value";
            case ADD_TX_LARGE_NRG: return "addPendingTransaction() with a large energy limit";
            case ADD_TX_NULL_TO: return "addPendingTransaction() with a null recipient";
            case ADD_FEW_TXS_AVG_DATA: return "addPendingTransactions() with a few transactions with average-sized data";
            case ADD_FEW_TXS_LARGE_DATA: return "addPendingTransactions() with a few transactions with large-sized data";
            case ADD_FEW_TXS_LARGE_NONCES: return "addPendingTransactions() with a few transactions with large nonces";
            case ADD_FEW_TXS_LARGE_NRGS: return "addPendingTransactions() with a few transactions with large energy limits";
            case ADD_FEW_TXS_LARGE_VALUES: return "addPendingTransactions() with a few transactions with large values";
            case ADD_FEW_TXS_MIXED: return "addPendingTransactions() with a few mixed transactions";
            case ADD_FEW_TXS_NULL_TOS: return "addPendingTransactions() with a few transactions with null recipients";
            case ADD_MANY_TXS_AVG_DATA: return "addPendingTransactions() with many transactions with average-sized data";
            case ADD_MANY_TXS_LARGE_DATA: return "addPendingTransactions() with many transactions with large-sized data";
            case ADD_MANY_TXS_LARGE_NONCES: return "addPendingTransactions() with many transactions with large nonces";
            case ADD_MANY_TXS_LARGE_NRGS: return "addPendingTransactions() with many transactions with large energy limits";
            case ADD_MANY_TXS_LARGE_VALUES: return "addPendingTransactions() with many transactions with large values";
            case ADD_MANY_TXS_MIXED: return "addPendingTransactions() with many mixed transactions";
            case ADD_MANY_TXS_NULL_TOS: return "addPendingTransactions() with many transactions with null recipients";
            default: return null;
        }
    }

    /**
     * Displays the records.
     */
    private void printRecords() {
        callOrder.add(0, Event.INST);
        for (Event event : callOrder) {
            System.out.printf(
                "Ran %s in %d milliseconds (%,d nanoseconds).\n",
                eventToString(event),
                TimeUnit.NANOSECONDS.toMillis(records.get(event)),
                records.get(event));
        }
    }

    //<-------------------------METHODS THAT PRODUCE EVENT ORDERINGS------------------------------->

    /**
     * Returns a randomized ordering of each possible Event to be called such that each Event
     * appears in the returned ordering exactly once. The INST event is the only event not returned
     * in this list.
     */
    private List<Event> getRandomCallOrder() {
        List<Event> calls = new ArrayList<>(Arrays.asList(Event.values()));
        calls.remove(Event.INST);
        Collections.shuffle(calls);
        return calls;
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

}
