package org.aion.zero.impl.valid;

import org.aion.base.util.ByteUtil;
import org.aion.zero.types.AionTransaction;
import org.junit.Test;

public class TransactionValidatorTest {
    @Test
    public void testExternalTransactionValidation() {
        String transactionHex = "0xf8ac823030a0a0ce840dd21665a62ecf45c6b4a9123a0b691dcdb0271e9a8b9e36cc0253cc418432333238808c30313634663833366561666184323731308a30323534306265343030823031b860ba81e864425aacc0292f7ce45230ddd70e6dcda550dee7b90a5885fade11b8c5e9aef713e31c1d435b235b420c8110368a4de590d6e0b98dc52fb84eff33bb72af04baad2ec42226e9655bab36e71680cdbb424d3004d374765c77be3408c10b";
        byte[] input = ByteUtil.hexStringToBytes(transactionHex);
        AionTransaction tx = new AionTransaction(input);
        TXValidator.isValid0(tx);
    }
}
