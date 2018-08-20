package org.aion;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKey;
import org.aion.mcf.account.AccountManager;
import org.aion.mcf.account.Keystore;
import org.aion.zero.types.AionTransaction;

import java.io.Console;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

public class ColdWallet {

    // keep the configuration simple
    static class Config {
        public Address accountAddress;
        public Address recipientAddress;
        public BigInteger nonce;
        public BigInteger value; // value IN AION
        public BigInteger energyPrice;
        public BigInteger energy;
    }

    /**
     * Smol application to generate commands for sending out raw transactions
     */
    public static void main(String[] args) {
        Config config = null;
        if ((config = processInput(args)) == null) {
            System.exit(1);
        }

        ECKey key = null;
        if ((key = processPassword(config)) == null) {
            System.exit(1);
        }

        /** (byte[] nonce, Address to, byte[] value, byte[] data, long nrg, long nrgPrice) **/
        AionTransaction transaction = new AionTransaction(
                config.nonce.toByteArray(),
                config.recipientAddress,
                config.value.toByteArray(),
                ByteUtil.EMPTY_BYTE_ARRAY,
                config.energy.longValueExact(),
                config.energyPrice.longValueExact());
        transaction.sign(key);

        printCurl(transaction.getEncoded());
    }

    static Config processInput(String[] args) {
        System.out.println("ColdWallet [v0.0.1] offline transaction creation utility");
        // setup
        Config config = new Config();
        Scanner sc = new Scanner(System.in);

        // process input account
        {
            System.out.print("Account Address: ");
            String in = sc.nextLine();
            try {
                config.accountAddress = Address.wrap(in);
            } catch (IllegalArgumentException e) {
                improperAddress(in);
                return null;
            }

            if (!doesAccountExist(config.accountAddress)) {
                System.out.println("Account not found: " + config.accountAddress);
                return null;
            }
        }

        // process recipient address
        {
            System.out.print("Recipient Address: ");
            String in = sc.nextLine();
            try {
                config.recipientAddress = Address.wrap(in);
            } catch (IllegalArgumentException e) {
                improperAddress(in);
                return null;
            }
        }

        {
            System.out.print("Account Nonce: ");
            BigInteger in = sc.nextBigInteger();
            if (in.signum() < 0) {
                return null;
            }
            config.nonce = in;
        }

        {
            System.out.print("Value (AION): ");
            BigInteger in = sc.nextBigInteger();

            if (in.signum() < 0) {
                return null;
            }
            config.value = in.multiply(BigInteger.TEN.pow(18));
        }

        config.energy = BigInteger.valueOf(21000L);
        config.energyPrice = BigInteger.TEN.pow(10);
        printConfig(config);
        return config;
    }

    static ECKey processPassword(Config config) {
        Console console = System.console();
        String password = new String(console.readPassword("Password: "));
        if (!AccountManager.inst().unlockAccount(config.accountAddress, password, 120)) {
            System.out.println("failed to unlock account: " + config.accountAddress);
            return null;
        }
        return AccountManager.inst().getKey(config.accountAddress);
    }

    static boolean doesAccountExist(Address accountAddress) {
        Set<Address> s = Arrays.stream(Keystore.list())
                .map(Address::wrap)
                .collect(Collectors.toSet());
        return s.contains(accountAddress);
    }

    static void improperAddress(String input) {
        System.out.println("Improper Address formatting: " + input);
    }

    static void printConfig(Config config) {
        System.out.println("accountAddress: " + config.accountAddress);
        System.out.println("recipientAddress: " + config.recipientAddress);
        System.out.println("nonce: " + config.nonce);
        System.out.println("value: " + config.value);
        System.out.println("energy: " + config.energy);
        System.out.println("energyPrice: " + config.energyPrice);
    }

    static void printCurl(byte[] signedTransaction) {
        int randomId = new Random().nextInt();
        StringBuilder builder = new StringBuilder();
        builder.append("curl -H \"Accept: application/json\" -H \"Content-Type: application/json\" -X POST  --data ");
        builder.append("'{");
        builder.append("\"jsonrpc\":\"2.0\",");
        builder.append("\"method\":\"eth_sendRawTransaction\",");
        builder.append("\"params\":[\"");
        builder.append(ByteUtil.toHexStringWithPrefix(signedTransaction));
        builder.append("\"],");
        builder.append("\"id\":");
        builder.append(Integer.toString(randomId));
        builder.append("}'");
        System.out.println(builder.toString());
    }
}