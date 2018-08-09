package org.aion.types;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.zero.types.AionTransaction;
import org.json.JSONObject;
import org.junit.Test;

import java.math.BigInteger;

/**
 * A set of tests on the Aion side for testing integration with
 * web3.
 */
public class AionTransactionIntegrationTest {

    private static String hex(byte[] input) {
        return ByteUtil.toHexString(input);
    }

    @Test
    public void basicEncodingTest() {
        byte[] nonce = BigInteger.ONE.toByteArray();
        Address to = new Address(HashUtil.h256("address".getBytes()));
        byte[] value = BigInteger.ONE.toByteArray();
        byte[] data = HashUtil.h256("data".getBytes());
        long nrg = 1_000_000L;
        long nrgPrice = 10_000_000_000L;

        ECKey key = ECKeyFac.inst().create();

        AionTransaction tx = new AionTransaction(nonce, to, value, data, nrg, nrgPrice);
        tx.sign(key);
        JSONObject obj = new JSONObject();

        obj.put("privateKey", ByteUtil.toHexString(key.getPrivKeyBytes()));

        JSONObject txObj = new JSONObject();
        txObj.put("nonce", hex(nonce));
        txObj.put("to", hex(to.toBytes()));
        txObj.put("value", hex(value));
        txObj.put("data", hex(data));
        txObj.put("nrg", nrg);
        txObj.put("nrgPrice", nrgPrice);
        txObj.put("type", tx.getType());
        txObj.put("timestamp", hex(tx.getTimeStamp()));

        obj.put("tx", txObj);

        obj.put("raw", hex(tx.getEncodedRaw()));
        obj.put("signed", hex(tx.getEncoded()));
        obj.put("ed_sig", hex(tx.getSignature().getSignature()));
        obj.put("aion_sig", hex(tx.getSignature().toBytes()));

        System.out.print(obj.toString(2));
    }
}
