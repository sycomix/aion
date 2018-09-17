package org.aion.zero.impl.blockchain.contracts;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;

/**
 * Series of tests to verify that edverify is able to compile
 * successfully with a modified solidity compiler, and properly encode
 * and decode the precompiled contract arguments
 */
public class EdVerifyIntegrationTest {

    byte[] edValidateBytecode = ByteUtil.hexStringToBytes("0x605060405234156100105760006000fd5b610015565b6101c9806100246000396000f30060506040526000356c01000000000000000000000000900463ffffffff1680633a0f2e51146100335761002d565b60006000fd5b341561003f5760006000fd5b6100a8600480808060100135903590600019169090916020019091929080806010013590359060001916909091602001909192908080601001359035906000191690909160200190919290808060100135903590600019169090916020019091929050506100c5565b604051808383825281601001526020019250505060405180910390f35b6000600060006000601060008d8d8d8d8d8d8d8d6000604051602001526040518089899060001916909060001916908252816010015260200187879060001916909060001916908252816010015260200185859060001916909060001916908252816010015260200183839060001916909060001916908252816010015260200198505050505050505050602060405180830381600087875af1151561016b5760006000fd5b50505060405180806010015190519091602001509150915081819350935061018e565b505098509896505050505050505600a165627a7a723058206af197b0fa36f70d817dc615b72771519117453fe1cca403161ae72a08eac1fa0029");

    // unused for now, but just in case we need it in the future
    String edValidateAbiJson = "[{\"constant\":false,\"inputs\":[{\"name\":\"messageHash\",\"type\":\"bytes32\"},{\"name\":\"publicKey\",\"type\":\"bytes32\"},{\"name\":\"sig1\",\"type\":\"bytes32\"},{\"name\":\"sig2\",\"type\":\"bytes32\"}],\"name\":\"validate\",\"outputs\":[{\"name\":\"\",\"type\":\"address\"}],\"payable\":false,\"type\":\"function\"}]";

    // abi
    String abi_validate = "validate(bytes32,bytes32,bytes32,bytes32)";
    byte[] abi_validateHash = ByteUtil.hexStringToBytes("0x3a0f2e51");

    @Test
    public void testVerifyPassingSignature() {
        String inputMessage = "hello world!";

        // first input, message hash
        byte[] inputMessageHash = HashUtil.h256(inputMessage.getBytes());

        ECKey signer = ECKeyFac.inst().create();
        ISignature signature = signer.sign(inputMessageHash);

        // second input, public key
        byte[] inputPublicKey = signer.getPubKey();

        ByteBuffer wrapped = ByteBuffer.wrap(signature.getSignature());
        wrapped.rewind();

        // input 3
        byte[] sigChunk1 = new byte[32];

        // input 4
        byte[] sigChunk2 = new byte[32];
        wrapped.get(sigChunk1);
        wrapped.get(sigChunk2);

        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .build();

        StandaloneBlockchain blockchain = bundle.bc;
        ECKey sender = bundle.privateKeys.get(0);

        // deployment transaction
        AionTransaction deployTx =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        edValidateBytecode,
                        1_000_000L,
                        1L);

        deployTx.sign(sender);

        AionBlock block = blockchain.createNewBlock(blockchain.getBestBlock(), Collections.singletonList(deployTx), true);
        assertThat(block.getTransactionsList().get(0)).isEqualTo(deployTx);
        blockchain.tryToConnect(block);

        // deploy status correct, now test functionality
        byte[] functionCall = ByteUtil.merge(abi_validateHash, inputMessageHash, inputPublicKey, sigChunk1, sigChunk2);
        AionTransaction verifyTx =
                new AionTransaction(
                        BigInteger.ONE.toByteArray(),
                        deployTx.getContractAddress(),
                        BigInteger.ZERO.toByteArray(),
                        functionCall,
                        2_000_000L,
                        1L);

        verifyTx.sign(sender);

        block = blockchain.createNewBlock(blockchain.getBestBlock(), Collections.singletonList(verifyTx), true);
        assertThat(block.getTransactionsList().get(0)).isEqualTo(verifyTx);

        ImportResult result = blockchain.tryToConnect(block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        AionTxInfo txInfo = blockchain.getTransactionInfo(verifyTx.getHash());
        assertThat(txInfo.getReceipt().getError()).isEmpty();
        assertThat(txInfo.getReceipt().getExecutionResult()).isEqualTo(signer.getAddress());
    }

    @Test
    public void testVerifyFailingSignature() {
        String inputMessage = "hello world!";

        // first input, message hash
        byte[] inputMessageHash = HashUtil.h256(inputMessage.getBytes());

        ECKey signer = ECKeyFac.inst().create();
        ISignature signature = signer.sign(inputMessageHash);

        // second input, public key
        byte[] inputPublicKey = signer.getPubKey();

        ByteBuffer wrapped = ByteBuffer.wrap(signature.getSignature());
        wrapped.rewind();

        // input 3
        byte[] sigChunk1 = new byte[32];

        // input 4
        byte[] sigChunk2 = new byte[32];

        // reverse the ordering of the chunks to break signature
        wrapped.get(sigChunk2);
        wrapped.get(sigChunk1);

        StandaloneBlockchain.Bundle bundle = new StandaloneBlockchain.Builder()
                .withDefaultAccounts()
                .withValidatorConfiguration("simple")
                .build();

        StandaloneBlockchain blockchain = bundle.bc;
        ECKey sender = bundle.privateKeys.get(0);

        // deployment transaction
        AionTransaction deployTx =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        null,
                        BigInteger.ZERO.toByteArray(),
                        edValidateBytecode,
                        1_000_000L,
                        1L);

        deployTx.sign(sender);

        AionBlock block = blockchain.createNewBlock(blockchain.getBestBlock(), Collections.singletonList(deployTx), true);
        assertThat(block.getTransactionsList().get(0)).isEqualTo(deployTx);
        blockchain.tryToConnect(block);

        // deploy status correct, now test functionality
        byte[] functionCall = ByteUtil.merge(abi_validateHash, inputMessageHash, inputPublicKey, sigChunk1, sigChunk2);
        AionTransaction verifyTx =
                new AionTransaction(
                        BigInteger.ONE.toByteArray(),
                        deployTx.getContractAddress(),
                        BigInteger.ZERO.toByteArray(),
                        functionCall,
                        2_000_000L,
                        1L);

        verifyTx.sign(sender);

        block = blockchain.createNewBlock(blockchain.getBestBlock(), Collections.singletonList(verifyTx), true);
        assertThat(block.getTransactionsList().get(0)).isEqualTo(verifyTx);

        ImportResult result = blockchain.tryToConnect(block);
        assertThat(result).isEqualTo(ImportResult.IMPORTED_BEST);
        AionTxInfo txInfo = blockchain.getTransactionInfo(verifyTx.getHash());
        assertThat(txInfo.getReceipt().getError()).isEmpty();
        assertThat(txInfo.getReceipt().getExecutionResult()).isEqualTo(Address.ZERO_ADDRESS().toBytes());
    }
}
