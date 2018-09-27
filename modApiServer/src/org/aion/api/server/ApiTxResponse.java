/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.api.server;

public class ApiTxResponse {

    public enum TxRspType {

        SUCCESS(0, "Transaction sent successfully"),
        INVALID_TX(1, "Invalid transaction object"),
        INVALID_FROM(2, "Invalid from address provided"),
        INVALID_ACCOUNT(3, "Account not found, or not unlocked"),
        EXCEPTION(4, "");

        private final int code;
        private final String message;

        TxRspType (int code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public int getCode() {
            return code;
        }
    }

    private final TxRspType type;
    private byte[] txHash;

    // Could just store the exception message string
    private Exception ex;

    public ApiTxResponse(TxRspType type) {
        this.type = type;
    }

    public ApiTxResponse(TxRspType type, byte[] txHash) {
        this.type = type;
        this.txHash = txHash;
    }

    public ApiTxResponse(TxRspType type, Exception ex) {
        this.type = type;
        this.ex = ex;
    }

    public TxRspType getType() {
        return type;
    }

    //Should only be called if tx was successfully sent
    public byte[] getTxHash() {
        return txHash;
    }

    public String getExceptionMsg() {
        return ex.getMessage();
    }
}
