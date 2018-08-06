package org.aion.zero.impl.valid;

import org.aion.base.util.ByteUtil;
import org.aion.zero.types.AionTransaction;
import org.junit.Test;

public class TxValidatorTest {
    @Test
    public void testDecoding() {
        byte[] encoded = ByteUtil.hexStringToBytes("0xf89a00a0a0614956c8a12c5bd127ad2f8d7fbba8399d955205fe740ef384f2f5e1a4f88b83015f908474657374845b65a00d82c3508301388000b860a0614956c8a12c5bd127ad2f8d7fbba8399d955205fe740ef384f2f5e1a4f88ba64874d47ff47d6f72b825310d9953f12937f310b257130f16560dbbfc433037fb35f5c617406efc765fa1510646926a380dbb7a1531f447d826ed592d64c40b");
        byte[] anotherEncoded = ByteUtil.hexStringToBytes("0xf89a00a0a08f63d6d7eaa31bf05dc0a9099d5668af042f83f372f77a13bf1dd0cb5dfff183015f908474657374845b65a56582c3508301388000b860a08f63d6d7eaa31bf05dc0a9099d5668af042f83f372f77a13bf1dd0cb5dfff1bf957633d593a23adc6fb3ae66aab70e2dae32c3399455fb89e6b42104c325c537263d4246dd777d3bd2378ff2a4917041aa4ba47464d101ed9005884c8f1005");
        AionTransaction transaction = new AionTransaction(anotherEncoded);
        System.out.println(transaction);
        System.out.println(TXValidator.isValid0(transaction));
    }
}
