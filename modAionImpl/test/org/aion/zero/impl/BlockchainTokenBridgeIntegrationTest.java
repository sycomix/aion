package org.aion.zero.impl;

import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.core.ImportResult;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.encoding.AbiEncoder;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.base.util.ByteUtil.*;

public class BlockchainTokenBridgeIntegrationTest {

    private byte[] TEST_ACCOUNT_PK = hexStringToBytes("0xa0247df1bf403ef3734dcf9852fc8f9bade101fa4b125a52374cf5a4c01fae5e3d5eb1ccadff7f62dca138158870ee51f6ea559c2a48d624be478993c4d1e9d8");
    private ECKey TEST_OWNER_EC = ECKeyFac.inst().fromPrivate(TEST_ACCOUNT_PK);

    private List<String> functionCalls = Arrays.asList(
            "owner()",
            "newOwner()",
            "actionMap(bytes32)",
            "ringMap(address)",
            "ringLocked()",
            "minThresh()",
            "memberCount()",
            "relayer()"
    );

    @Test
    public void testTokenBridgeAppearence() {
        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
                .withDefaultAccounts(Arrays.asList(TEST_OWNER_EC, ECKeyFac.inst().create()))
                .withValidatorConfiguration("simple")
                .build();

        // even the most simple call, a query, should fail
        ECKey randomAccount = bundle.privateKeys.get(1);
        BigInteger nonce = BigInteger.ZERO;
        for (String func : functionCalls) {
            AionTransaction tx = bridgeCall(new AbiEncoder(func).encodeBytes(), nonce);
            tx.sign(randomAccount);
            AionBlock block = bundle.bc.createNewBlock(bundle.bc.getBestBlock(), Collections.singletonList(tx), true);
            assertThat(block.getTransactionsList().size()).isEqualTo(1);

            ImportResult result = bundle.bc.tryToConnect(block);
            assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);

            AionTxInfo info = bundle.bc.getTransactionInfo(tx.getHash());

            assertThat(info.getReceipt().isValid()).isTrue();
            assertThat(info.getReceipt().getExecutionResult()).isEmpty();

            nonce = nonce.add(BigInteger.ONE);
        }
    }

    private static AionTransaction bridgeCall(byte[] payload, BigInteger nonce) {
        return new AionTransaction(
                nonce.toByteArray(),
                ContractFactory.getATBContractAddress(),
                BigInteger.ZERO.toByteArray(),
                payload,
                100000L,
                BigInteger.TEN.pow(10).longValueExact());
    }
}
