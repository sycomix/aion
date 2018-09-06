package org.aion.zero.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.aion.base.type.Address;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.evtmgr.impl.mgr.EventMgrA0;
import org.aion.mcf.core.ImportResult;
import org.aion.mcf.vm.Constants;
import org.aion.zero.impl.StandaloneBlockchain.Builder;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.tx.TxCollector;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.valid.TXValidator;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AionPendingStateImplTest {
    private AionPendingStateImpl pendingStateImpl;
    private static int broadcastBufferSize = 0;

    @Before
    public void setup() {
        CfgAion cfgAion = CfgAion.inst();
        StandaloneBlockchain.Bundle bundle = (new Builder())
            .withValidatorConfiguration("simple")
            .withDefaultAccounts()
            .build();
        StandaloneBlockchain blockchain = bundle.bc;
        blockchain.setEventManager(new EventMgrA0(new Properties()));
        AionRepositoryImpl repo = blockchain.getRepository();
        pendingStateImpl = AionPendingStateImpl.createForTesting(cfgAion, blockchain, repo);
    }

    @After
    public void tearDown() {
        drainBroadcastBuffer();
        pendingStateImpl = null;
    }

    //<------------------------------------------TESTS--------------------------------------------->

    /**
     * Expected behaviour: the transaction will be broadcast.
     * That is, it will be sitting in the TxCollector's queue that belongs to AionImpl.
     */
    @Test
    public void testAddPendingTxNotLoadingIsSeedIsValidTx() {
        pendingStateImpl.setIsLoadingPendingTx(false);
        pendingStateImpl.setIsSeed(true);

        // Valid tx for contract creation.
        AionTransaction tx = makeValidTransaction(true);
        broadcastBufferSize += tx.getEncoded().length;
        doAndCheckTransactionBroadcast(tx, true);

        // Valid tx not for contract creation.
        tx = makeValidTransaction(false);
        broadcastBufferSize += tx.getEncoded().length;
        doAndCheckTransactionBroadcast(tx, true);
    }

    /**
     * Expected behaviour: the transaction will not be broadcast.
     * That is, the TxCollector's queue that belongs to AionImpl will be empty.
     *
     * In the case that the signature is missing from the tx, we expect a NullPointerException to be
     * thrown by the ByteArrayWrapper that tries to wrap the transaction in TXValidator.
     */
    @Test
    public void testAddPendingTxNotLoadingIsSeedNotValidTx() {
        pendingStateImpl.setIsLoadingPendingTx(false);
        pendingStateImpl.setIsSeed(true);

        for (InvalidParameter param : InvalidParameter.values()) {
            if (!param.equals(InvalidParameter.SIGNATURE)) {
                // Invalid tx for contract creation.
                AionTransaction tx = makeInvalidTransaction(true, param);
                doAndCheckTransactionBroadcast(tx, false);

                // Invalid tx not for contract creation.
                tx = makeInvalidTransaction(false, param);
                doAndCheckTransactionBroadcast(tx, false);
            } else {
                // Handle unsigned transactions separately.
                try {
                    AionTransaction tx = makeInvalidTransaction(true, param);
                    doAndCheckTransactionBroadcast(tx, false);
                    fail("No NullPointerException thrown.");
                } catch (NullPointerException e) {
                    // expected behaviour.
                }

                try {
                    AionTransaction tx = makeInvalidTransaction(false, param);
                    doAndCheckTransactionBroadcast(tx, false);
                    fail("No NullPointerException thrown.");
                } catch (NullPointerException e) {
                    // expected behaviour.
                }
            }
        }
    }

    /**
     * Expected behaviour: the transaction will be broadcast.
     * That is, it will be sitting in the TxCollector's queue that belongs to AionImpl.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedNotNetworkBestIsValidTx() {
        pendingStateImpl.setIsLoadingPendingTx(false);
        pendingStateImpl.setIsSeed(false);
        pendingStateImpl.setIsCloseToNetworkBest(false);

        // Valid tx for contract creation.
        AionTransaction tx = makeValidTransaction(true);
        broadcastBufferSize += tx.getEncoded().length;
        doAndCheckTransactionBroadcast(tx, true);

        // Valid tx not for contract creation.
        tx = makeValidTransaction(false);
        broadcastBufferSize += tx.getEncoded().length;
        doAndCheckTransactionBroadcast(tx, true);
    }

    /**
     * Expected behaviour: the transaction will not be broadcast.
     * That is, the TxCollector's queue that belongs to AionImpl will be empty.
     *
     * In the case that the signature is missing from the tx, we expect a NullPointerException to be
     * thrown by the ByteArrayWrapper that tries to wrap the transaction in TXValidator.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedNotNetworkBestNotValidTx() {
        pendingStateImpl.setIsLoadingPendingTx(false);
        pendingStateImpl.setIsSeed(false);
        pendingStateImpl.setIsCloseToNetworkBest(false);

        for (InvalidParameter param : InvalidParameter.values()) {
            if (!param.equals(InvalidParameter.SIGNATURE)) {
                // Invalid tx for contract creation.
                AionTransaction tx = makeInvalidTransaction(true, param);
                doAndCheckTransactionBroadcast(tx, false);

                // Invalid tx not for contract creation.
                tx = makeInvalidTransaction(false, param);
                doAndCheckTransactionBroadcast(tx, false);
            } else {
                // Handle unsigned transactions separately.
                try {
                    AionTransaction tx = makeInvalidTransaction(true, param);
                    doAndCheckTransactionBroadcast(tx, false);
                    fail("No NullPointerException thrown.");
                } catch (NullPointerException e) {
                    // expected behaviour.
                }

                try {
                    AionTransaction tx = makeInvalidTransaction(false, param);
                    doAndCheckTransactionBroadcast(tx, false);
                    fail("No NullPointerException thrown.");
                } catch (NullPointerException e) {
                    // expected behaviour.
                }
            }
        }
    }

    //---------

    /**
     * Tests:
     *   1. transaction nonce is larger than the best nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferIsEnabledIsValidTx1() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is equal to the best nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferIsEnabledIsValidTx2() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is less than transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferIsEnabledIsValidTx3() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is equal to transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferIsEnabledIsValidTx4() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is larger than transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferIsEnabledIsValidTx5() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is larger than the best nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferNotEnabledIsValidTx1() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is equal to the best nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferNotEnabledIsValidTx2() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is less than transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferNotEnabledIsValidTx3() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is equal to transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferNotEnabledIsValidTx4() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is larger than transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferNotEnabledIsValidTx5() {
        //TODO
    }

    //-----------

    /**
     * Tests:
     *   1. transaction nonce is larger than the best nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferIsEnabledNotValidTx1() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is equal to the best nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferIsEnabledNotValidTx2() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is less than transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferIsEnabledNotValidTx3() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is equal to transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferIsEnabledNotValidTx4() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is larger than transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferIsEnabledNotValidTx5() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is larger than the best nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferNotEnabledNotValidTx1() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is equal to the best nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferNotEnabledNotValidTx2() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is less than transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferNotEnabledNotValidTx3() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is equal to transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferNotEnabledNotValidTx4() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is larger than transaction nonce.
     */
    @Test
    public void testAddPendingTxIsLoadingBufferNotEnabledNotValidTx5() {
        //TODO
    }

    //------------

    /**
     * Tests:
     *   1. transaction nonce is larger than the best nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferIsEnabledIsValidTx1() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is equal to the best nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferIsEnabledIsValidTx2() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is less than transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferIsEnabledIsValidTx3() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is equal to transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferIsEnabledIsValidTx4() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is larger than transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferIsEnabledIsValidTx5() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is larger than the best nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferNotEnabledIsValidTx1() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is equal to the best nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferNotEnabledIsValidTx2() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is less than transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferNotEnabledIsValidTx3() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is equal to transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferNotEnabledIsValidTx4() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is larger than transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferNotEnabledIsValidTx5() {
        //TODO
    }

    //------------

    /**
     * Tests:
     *   1. transaction nonce is larger than the best nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferIsEnabledNotValidTx1() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is equal to the best nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferIsEnabledNotValidTx2() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is less than transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferIsEnabledNotValidTx3() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is equal to transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferIsEnabledNotValidTx4() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is larger than transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferIsEnabledNotValidTx5() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is larger than the best nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferNotEnabledNotValidTx1() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is equal to the best nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferNotEnabledNotValidTx2() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is less than transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferNotEnabledNotValidTx3() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is equal to transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferNotEnabledNotValidTx4() {
        //TODO
    }

    /**
     * Tests:
     *   1. transaction nonce is less than the best nonce.
     *   2. best repo nonce is larger than transaction nonce.
     */
    @Test
    public void testAddPendingTxNotLoadingNotSeedIsNetworkBestBufferNotEnabledNotValidTx5() {
        //TODO
    }

    //-----------

    /**
     * Expected behaviour: for all transactions -- broadcast the transaction iff it is valid.
     * If tx is broadcast it will be in the queue in the TxCollector belonging to AionImpl.
     */
    @Test
    public void testAddPendingTxsNotLoadingIsSeed() {
        pendingStateImpl.setIsLoadingPendingTx(false);
        pendingStateImpl.setIsSeed(true);

        Pair<List<AionTransaction>, List<Boolean>> randomTxs = makeRandomTransactions();
        List<AionTransaction> transactions = randomTxs.getLeft();
        List<Boolean> txValidities = randomTxs.getRight();
        assertEquals(transactions.size(), txValidities.size());

        int numTxs = transactions.size();
        for (int i = 0; i < numTxs; i++) {
            if (transactions.get(i).getSignature() == null) {
                try {
                    doAndCheckTransactionBroadcast(transactions.get(i), txValidities.get(i));
                    fail("No NullPointerException thrown.");
                } catch (NullPointerException e) {
                    // expected behaviour.
                }
            } else {
                doAndCheckTransactionBroadcast(transactions.get(i), txValidities.get(i));
            }
        }
    }

    /**
     * Expected behaviour: for all transactions -- broadcast the transaction iff it is valid.
     * If tx is broadcast it will be in the queue in the TxCollector belonging to AionImpl.
     */
    @Test
    public void testAddPendingTxsNotLoadingNotSeedNotNetworkBest() {
        pendingStateImpl.setIsLoadingPendingTx(false);
        pendingStateImpl.setIsSeed(false);
        pendingStateImpl.setIsCloseToNetworkBest(false);

        Pair<List<AionTransaction>, List<Boolean>> randomTxs = makeRandomTransactions();
        List<AionTransaction> transactions = randomTxs.getLeft();
        List<Boolean> txValidities = randomTxs.getRight();
        assertEquals(transactions.size(), txValidities.size());

        int numTxs = transactions.size();
        for (int i = 0; i < numTxs; i++) {
            if (transactions.get(i).getSignature() == null) {
                try {
                    doAndCheckTransactionBroadcast(transactions.get(i), txValidities.get(i));
                    fail("No NullPointerException thrown.");
                } catch (NullPointerException e) {
                    // expected behaviour.
                }
            } else {
                doAndCheckTransactionBroadcast(transactions.get(i), txValidities.get(i));
            }
        }
    }

    //<--------------------------------------DIRECT HELPERS---------------------------------------->

    private enum InvalidParameter { NONCE, VALUE, DATA, NRG, PRICE, SIGNATURE }

    /**
     * Returns a Pair of two lists. The lefthand list is a list of numTxs transactions. The
     * righthand list is a list of numTxs booleans.
     * The maximum number of transactions are returned such that the broadcast cache is never
     * flushed -- this way we can actually see what is inside the cache.
     *
     * The following correspondence between the two lists exists:
     *   the i'th transaction is valid iff the i'th boolean is true
     *   otherwise the i'th transaction is invalid.
     *
     * @return a random list of transactions and list of corresponding booleans.
     */
    private Pair<List<AionTransaction>, List<Boolean>> makeRandomTransactions() {
        TxCollector collector = AionImpl.inst().getCollector();
        List<AionTransaction> transactions = new ArrayList<>();
        List<Boolean> txValidities = new ArrayList<>();
        Random random = new Random();

        while (true) {
            if (random.nextBoolean()) {
                // We make a valid transaction here. We break if the cache size will cause a flush.
                AionTransaction transaction = makeValidTransaction(random.nextBoolean());
                broadcastBufferSize += transaction.getEncoded().length;
                if (broadcastBufferSize >= collector.getMaxTxBufferSize()) {
                    break;
                }
                transactions.add(transaction);
                txValidities.add(true);
            } else {
                // We make an invalid transaction here.
                int randomIndex = RandomUtils.nextInt(0, InvalidParameter.values().length);
                InvalidParameter param = InvalidParameter.values()[randomIndex];
                transactions.add(makeInvalidTransaction(random.nextBoolean(), param));
                txValidities.add(false);
            }
        }
        return Pair.of(transactions, txValidities);
    }

    /**
     * Returns a new valid AionTransaction that is for contract creation iff isForContract is true.
     *
     * @param isForContract Whether or not the transaction is for contract creation.
     * @return a new valid AionTransaction.
     */
    private AionTransaction makeValidTransaction(boolean isForContract) {
        ECKey key = ECKeyFac.inst().create();
        Address to = (isForContract) ? null : makeAccount();
        byte[] nonce = BigInteger.ZERO.toByteArray();
        byte[] value = BigInteger.ZERO.toByteArray();
        byte[] data = new byte[1];
        long timestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        long nrg = getValidNrgLimit(isForContract);
        long nrgPrice = 0;

        AionTransaction tx =  new AionTransaction(nonce, to, value, data, nrg, nrgPrice);
        tx.setTimeStamp(timestamp);
        tx.setBlockHash(RandomUtils.nextBytes(32));
        tx.sign(key);
        return tx;
    }

    /**
     * Returns a new invalid AionTransaction that is for contract creation iff isForContract is
     * true.
     *
     * @param isForContract Whether or not the transaction is for contract creation.
     * @param param The transaction parameter to make invalid.
     * @return a new invalid AionTransaction.
     */
    private AionTransaction makeInvalidTransaction(boolean isForContract, InvalidParameter param) {
        ECKey key = ECKeyFac.inst().create();
        Address to = (isForContract) ? null : makeAccount();
        byte[] bigNum = BigInteger.TWO.pow(130).toByteArray();
        byte[] nonce = (param.equals(InvalidParameter.NONCE)) ? bigNum : BigInteger.ZERO.toByteArray();
        byte[] value = (param.equals(InvalidParameter.VALUE)) ? bigNum : BigInteger.ZERO.toByteArray();
        byte[] data = (param.equals(InvalidParameter.DATA)) ? null : new byte[1];
        long timestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        long nrgPrice = (param.equals(InvalidParameter.PRICE)) ? -1 : 1;
        long nrg = (param.equals(InvalidParameter.NRG))
            ? getInvalidNrgLimit(isForContract)
            : getValidNrgLimit(isForContract);

        AionTransaction tx =  new AionTransaction(nonce, to, value, data, nrg, nrgPrice);
        tx.setTimeStamp(timestamp);
        tx.setBlockHash(RandomUtils.nextBytes(32));
        if (!param.equals(InvalidParameter.SIGNATURE)) {
            tx.sign(key);
        }
        return tx;
    }

    /**
     * Calls AionPendingStateImpl's addPendingTransaction() on tx and if isBroadcast is true this
     * method checks that it was broadcast, otherwise it checks that it was not broadcast.
     *
     * @param tx The transaction to use.
     * @param isBroadcast Whether or not this method should expect tx to be broadcast or not.
     */
    private void doAndCheckTransactionBroadcast(AionTransaction tx, boolean isBroadcast) {
        TxCollector collector = AionImpl.inst().getCollector();
        int bufferSize = collector.getCollectedTransactions().size();
        pendingStateImpl.addPendingTransaction(tx);
        if (isBroadcast) {
            assertEquals(bufferSize + 1, collector.getCollectedTransactions().size());
            Collection<AionTransaction> txs = new LinkedList<>(collector.getCollectedTransactions());
            assertTrue(txs.contains(tx));
        } else {
            assertEquals(bufferSize, collector.getCollectedTransactions().size());
        }
    }

    /**
     * Drains the broadcast buffer so that once this method returns the broadcast buffer in
     * AionImpl's TxCollector has zero transactions and its buffer size is zero.
     */
    private void drainBroadcastBuffer() {
        // Strategy: add txs until the buffer flushes.
        TxCollector collector = AionImpl.inst().getCollector();
        while (true) {
            AionTransaction transaction = makeValidTransaction(true);
            broadcastBufferSize += transaction.getEncoded().length;
            if (broadcastBufferSize >= collector.getMaxTxBufferSize()) {
                pendingStateImpl.addPendingTransaction(transaction);
                assertEquals(0, collector.getCollectedTransactions().size());
                broadcastBufferSize = 0;
                return;
            } else {
                pendingStateImpl.addPendingTransaction(transaction);
            }
        }
    }

    //<------------------------------------INDIRECT HELPERS---------------------------------------->

    /**
     * Returns a new randomly-generated Address.
     *
     * @return a new Address.
     */
    private Address makeAccount() {
        return new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN));
    }

    /**
     * Returns a valid energy limit for a transaction.
     *
     * @param isForContract Whether or not the transaction is for contract creation.
     * @return a valid energy limit.
     */
    private long getValidNrgLimit(boolean isForContract) {
        return (isForContract) ? Constants.NRG_TX_CREATE : Constants.NRG_TRANSACTION;
    }

    /**
     * Returns an invalid energy limit for a transaction.
     *
     * @param isForContract Whether or not the transaction is for contract creation.
     * @return an invalid energy limit.
     */
    private long getInvalidNrgLimit(boolean isForContract) {
        return getValidNrgLimit(isForContract) - 1;
    }

}
