package org.aion.mcf.blockchain;

public enum AddTxResponse {
    SUCCESS(),
    INVALID_TX(),
    INVALID_TX_NRG_PRICE(),
    INVALID_FROM(),
    INVALID_ACCOUNT(),
    ALREADY_CACHED(),
    CACHED_NONCE(),
    CACHED_POOLMAX(),
    REPAYTX_POOL_EXCEPTION(),
    REPAYTX_LOWPRICE(),
    DROPPED(),
    EXCEPTION(),
    UNKNOWN()
}
