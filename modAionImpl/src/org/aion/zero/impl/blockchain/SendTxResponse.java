package org.aion.zero.impl.blockchain;

public enum SendTxResponse {

    SUCCESS(),
    INVALID_TX(),
    INVALID_FROM(),
    INVALID_ACCOUNT(),
    EXCEPTION()
}
