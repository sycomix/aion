package org.aion.zero.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
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
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AionPendingStateImplTest {
    private AionPendingStateImpl pendingStateImpl;

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
        doAndCheckTransactionBroadcast(tx, true);

        // Valid tx not for contract creation.
        tx = makeValidTransaction(false);
        doAndCheckTransactionBroadcast(tx, true);
    }

    /**
     * Expected behaviour: the transaction will not be broadcast.
     * That is, the TxCollector's queue that belongs to AionImpl will be empty.
     *
     * In the case that the signature is missing from the tx, we expect a NullPointerException to be
     * thrown by the ByteArrayWrapper that tries to wrap the transaction in TXValidator.
     * TODO: verify this NPE is actually what we want here.
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
        doAndCheckTransactionBroadcast(tx, true);

        // Valid tx not for contract creation.
        tx = makeValidTransaction(false);
        doAndCheckTransactionBroadcast(tx, true);
    }

    /**
     * Expected behaviour: the transaction will not be broadcast.
     * That is, the TxCollector's queue that belongs to AionImpl will be empty.
     *
     * In the case that the signature is missing from the tx, we expect a NullPointerException to be
     * thrown by the ByteArrayWrapper that tries to wrap the transaction in TXValidator.
     * TODO: verify this NPE is actually what we want here.
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

    /**
     * Expected behaviour: for all transactions -- broadcast the transaction iff it is valid.
     * If tx is broadcast it will be in the queue in the TxCollector belonging to AionImpl.
     */
    @Test
    public void testAddPendingTxsNotLoadingIsSeed() {
        pendingStateImpl.setIsLoadingPendingTx(false);
        pendingStateImpl.setIsSeed(true);

        int numTxs = 5_000;
        Pair<List<AionTransaction>, List<Boolean>> randomTxs = makeRandomTransactions(numTxs);
        List<AionTransaction> transactions = randomTxs.getLeft();
        List<Boolean> txValidities = randomTxs.getRight();
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
        //TODO
    }

    @Test
    public void testUnsignedTx() {
        StandaloneBlockchain.Bundle bundle = (new Builder())
            .withValidatorConfiguration("simple")
            .withDefaultAccounts()
            .build();
        StandaloneBlockchain blockchain = bundle.bc;

//        AionTransaction tx = makeValidTransaction(true);
        AionTransaction tx = makeInvalidTransaction(true, InvalidParameter.SIGNATURE);
        AionBlock block = blockchain.getBlockByNumber(0);
//        AionBlock newBlock = blockchain.createNewBlock(block, Collections.singletonList(tx), false);
//        ImportResult res = blockchain.tryToConnect(newBlock);
//        System.err.println(res);

        AionBlock b = new AionBlock(block.getHash(), block.getCoinbase(), block.getLogBloom(), block.getDifficulty(),
            block.getNumber() + 1, block.getTimestamp() + 1, block.getExtraData(),
            BigInteger.ZERO.toByteArray(), block.getReceiptsRoot(), new byte[1], new byte[1],
            Collections.singletonList(tx), new byte[1], 0, 1_000_000);
        blockchain.tryToConnect(b);
    }

    //<--------------------------------------DIRECT HELPERS---------------------------------------->

    private enum InvalidParameter { NONCE, VALUE, DATA, NRG, PRICE, SIGNATURE }

    /**
     * Returns a Pair of two lists. The lefthand list is a list of numTxs transactions. The
     * righthand list is a list of numTxs booleans.
     *
     * The following correspondence between the two lists exists:
     *   the i'th transaction is valid iff the i'th boolean is true
     *   otherwise the i'th transaction is invalid.
     *
     * @param numTxs The number of transactions to make.
     * @return a random list of transactions and list of corresponding booleans.
     */
    private Pair<List<AionTransaction>, List<Boolean>> makeRandomTransactions(int numTxs) {
        List<AionTransaction> transactions = new ArrayList<>();
        List<Boolean> txValidities = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < numTxs; i++) {
            if (random.nextBoolean()) {
                // We make a valid transaction here.
                transactions.add(makeValidTransaction(random.nextBoolean()));
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
        long nrg = 1_000_000;
//        long nrg = (param.equals(InvalidParameter.NRG))
//            ? getInvalidNrgLimit(isForContract)
//            : getValidNrgLimit(isForContract);

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
        assertEquals(0, collector.getCollectedTransactions().size());
        pendingStateImpl.addPendingTransaction(tx);
        if (isBroadcast) {
            assertEquals(1, collector.getCollectedTransactions().size());
            assertEquals(tx, collector.getCollectedTransactions().poll());
        } else {
            assertEquals(0, collector.getCollectedTransactions().size());
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
