package org.aion.precompiled.contracts.ATB;

import org.aion.base.type.Address;
import org.aion.base.util.Hex;
import org.aion.mcf.vm.types.Bloom;
import org.aion.mcf.vm.types.Log;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;

import java.util.List;

public class YulongsAuditTest {
    @Test
    public void testNullDataInLog() {
        AionTxReceipt receipt = new AionTxReceipt(new byte[32],
                new Bloom(new byte[0]),
                List.of(new Log(Address.ZERO_ADDRESS(), List.of(new byte[0]), null)));
        System.out.println(Hex.toHexString(receipt.getEncoded()));
    }
}
