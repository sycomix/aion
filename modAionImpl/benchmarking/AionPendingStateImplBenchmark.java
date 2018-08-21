import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

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

    private static final int FEW_THREADS = 5;
    private static final int MANY_THREADS = 50;
    private static final int FEW_REQUESTS = 300;
    private static final int AVG_REQUESTS = 10_000;
    private static final int MANY_REQUESTS = 100_000;
    private static final int FEW_TXS = 20;
    private static final int MANY_TXS = 1_500;
    private static final int AVG_DATA_SIZE = 2_500;
    private static final int LARGE_DATA_SIZE = 50_000;
    private static final BigInteger LARGE_BIG_INT = BigInteger.TWO.pow(1_000);

    /**
     * enum to trigger a call to a specific method.
     */
    private enum Event {
        INST, GET_REPO_FEW_FEW, GET_REPO_FEW_AVG, GET_REPO_FEW_MANY, GET_REPO_MANY_FEW,
        GET_REPO_MANY_AVG, GET_REPO_MANY_MANY, GET_PENDING_TX, ADD_TX_AVG_DATA,
        ADD_TX_LARGE_DATA, ADD_TX_LARGE_NONCE, ADD_TX_LARGE_VALUE, ADD_TX_LARGE_NRG, ADD_TX_NULL_TO,
        ADD_FEW_TXS_AVG_DATA, ADD_FEW_TXS_LARGE_DATA, ADD_FEW_TXS_LARGE_NONCES,
        ADD_FEW_TXS_LARGE_VALUES, ADD_FEW_TXS_LARGE_NRGS, ADD_FEW_TXS_NULL_TOS, ADD_FEW_TXS_MIXED,
        ADD_MANY_TXS_AVG_DATA, ADD_MANY_TXS_LARGE_DATA, ADD_MANY_TXS_LARGE_NONCES,
        ADD_MANY_TXS_LARGE_VALUES, ADD_MANY_TXS_LARGE_NRGS, ADD_MANY_TXS_NULL_TOS,
        ADD_MANY_TXS_MIXED
    }

    /**
     * enum to force a call down a specific code path.
     */
    private enum CodePath {
        NONE, IS_SEED, NOT_SEED, NOT_SEED_IS_BACKUP, NOT_SEED_IS_BACKUP_BUFFER_ENABLED
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
        records = new HashMap<>();
        orderOfCalls = new ArrayList<>();
        AionBlockchainImpl.inst().setEventManager(new EventMgrA0(new Properties())); // is this correct setup?
    }

    @Test
    public void testRandomizedBenchmarking() {
        makeCall(new BenchmarkCondition(Event.INST));
        orderOfCalls = getRandomCallOrder();
        for (BenchmarkCondition condition : orderOfCalls) {
            makeCall(condition);
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
        storeRecord(new BenchmarkCondition(Event.INST), end - start);
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
        storeRecord(new BenchmarkCondition(event), end - start);
    }

    /**
     * Calls getPendingTransactions().
     */
    private void recordGetPendingTransactions() {
        long start = System.nanoTime();
        pendingState.getPendingTransactions();
        long end = System.nanoTime();
        storeRecord(new BenchmarkCondition(Event.GET_PENDING_TX), end - start);
    }

    /**
     * Calls addPendingTransaction() using a transaction that corresponds to the Event event.
     */
    private void recordAddPendingTransaction(Event event) {
        AionTransaction transaction = getTransactionForEvent(event);
        long start = System.nanoTime();
        pendingState.addPendingTransaction(transaction);
        long end = System.nanoTime();
        storeRecord(new BenchmarkCondition(event), end - start);
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
        storeRecord(new BenchmarkCondition(event), end - start);
    }

    //<----------------------------GRUNT WORK HELPER METHODS--------------------------------------->

    /**
     * Sets whatever fields or objects are needed to whatever state is needed in order to force the
     * code down the specified path.
     */
    private void setUpCodePath(CodePath path) {
        // ensure previous rounds do not interfere with this round by resetting.
        CfgAion.inst().getConsensus().seed = false;
        CfgAion.inst().getTx().buffer = false;
        CfgAion.inst().getTx().poolBackup = false;

        // enable only what we want to be set for this round.
        switch (path) {
            case IS_SEED: CfgAion.inst().getConsensus().seed = true;
                break;
            case NOT_SEED_IS_BACKUP_BUFFER_ENABLED: CfgAion.inst().getTx().buffer = true;
            case NOT_SEED_IS_BACKUP: CfgAion.inst().getTx().poolBackup = true;
            case NOT_SEED: CfgAion.inst().getConsensus().seed = false;
                break;
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
                RandomUtils.nextBytes(AVG_DATA_SIZE),
                nrg,
                nrgPrice);
                break;
            case ADD_TX_LARGE_DATA: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                value.toByteArray(),
                RandomUtils.nextBytes(LARGE_DATA_SIZE),
                nrg,
                nrgPrice);
                break;
            case ADD_TX_LARGE_NONCE: transaction = new AionTransaction(
                LARGE_BIG_INT.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                value.toByteArray(),
                RandomUtils.nextBytes(AVG_DATA_SIZE),
                nrg,
                nrgPrice);
                break;
            case ADD_TX_LARGE_NRG: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                value.toByteArray(),
                RandomUtils.nextBytes(AVG_DATA_SIZE),
                Long.MAX_VALUE,
                nrgPrice);
                break;
            case ADD_TX_LARGE_VALUE: transaction = new AionTransaction(
                nonce.toByteArray(),
                new Address(key.getAddress()),
                new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN)),
                LARGE_BIG_INT.toByteArray(),
                RandomUtils.nextBytes(AVG_DATA_SIZE),
                nrg,
                nrgPrice);
                break;
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
    private void makeCall(BenchmarkCondition condition) {
        setUpCodePath(condition.path);
        switch (condition.event) {
            case INST: recordInst();
                break;
            case GET_REPO_FEW_FEW:
            case GET_REPO_FEW_AVG:
            case GET_REPO_FEW_MANY:
            case GET_REPO_MANY_FEW:
            case GET_REPO_MANY_AVG:
            case GET_REPO_MANY_MANY: recordGetRepository(condition.event);
                break;
            case GET_PENDING_TX: recordGetPendingTransactions();
                break;
            case ADD_TX_AVG_DATA:
            case ADD_TX_LARGE_DATA:
            case ADD_TX_LARGE_NONCE:
            case ADD_TX_LARGE_NRG:
            case ADD_TX_LARGE_VALUE:
            case ADD_TX_NULL_TO: recordAddPendingTransaction(condition.event);
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
            case ADD_MANY_TXS_NULL_TOS: recordAddPendingTransactions(condition.event);
                break;
        }
    }

    //<-------------------------METHODS THAT PRODUCE EVENT ORDERINGS------------------------------->

    /**
     * Returns a randomized ordering of each possible event (and each relevant code path
     * combination).
     */
    private List<BenchmarkCondition> getRandomCallOrder() {
        List<Event> events = new ArrayList<>(Arrays.asList(Event.values()));
        List<BenchmarkCondition> ordering = new ArrayList<>();
        for (Event event : events) {
            ordering.add(new BenchmarkCondition(event));
        }
        Collections.shuffle(ordering);
        return ordering;
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
     * Returns a string representation for path for displaying.
     */
    private String codePathToString(CodePath path) {
        switch (path) {
            case IS_SEED: return "isSeed";
            case NOT_SEED: return "!isSeed";
            case NOT_SEED_IS_BACKUP: return "!isSeed AND poolBackUp";
            case NOT_SEED_IS_BACKUP_BUFFER_ENABLED: return "!isSeed AND poolBackUp AND bufferEnable";
            case NONE: return "default";
            default: return null;
        }
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
     * A class containing the conditions in which a particular benchmark call was called.
     */
    private class BenchmarkCondition {
        private Event event;
        private CodePath path;

        BenchmarkCondition(Event event, CodePath path) {
            this.event = event;
            this.path = path;
        }

        BenchmarkCondition(Event event) {
            this(event, CodePath.NONE);
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) { return false; }
            if (!(other instanceof BenchmarkCondition)) { return false; }
            BenchmarkCondition otherBenchmarkCondition = (BenchmarkCondition) other;
            if (this.event != otherBenchmarkCondition.event) { return false; }
            return this.path == otherBenchmarkCondition.path;
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

}
