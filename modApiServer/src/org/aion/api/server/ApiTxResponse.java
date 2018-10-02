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

import org.aion.mcf.blockchain.AddTxResponse;

public class ApiTxResponse {

    private final AddTxResponse rsp;

    private byte[] txHash;

    // Could just store the exception message string
    private Exception ex;

    ApiTxResponse(AddTxResponse rsp) {
        this.rsp = rsp;
    }

    ApiTxResponse(AddTxResponse rsp, byte[] txHash) {
        this.rsp = rsp;
        this.txHash = txHash;
    }

    ApiTxResponse(AddTxResponse rsp, Exception ex) {
        this.rsp = rsp;
        this.ex = ex;
    }

    public AddTxResponse getType() {
        return rsp;
    }

    public String getMessage() {
        switch (rsp) {
            case INVALID_TX:
                return ("Invalid transaction object");
            case INVALID_FROM:
                return ("Invalid from address provided");
            case INVALID_ACCOUNT:
                return ("Account not found, or not unlocked");
            case EXCEPTION:
                return (ex.getMessage());
            case SUCCESS:
                return ("Transaction sent successfully");
            default:
                //TODO AAYUSH REPLACE THIS
                return ("");
        }
    }

    //Should only be called if tx was successfully sent
    public byte[] getTxHash() {
        return txHash;
    }

}
