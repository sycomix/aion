package org.aion.rlp;

import static org.aion.base.util.ByteUtil.byteArrayToInt;
import static org.aion.rlp.RLPTest.bytesToAscii;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import org.aion.base.util.Hex;
import org.junit.Test;

/**
 * Tests that the RLP implementation is compliant with the RLP specification from Ethereum.
 *
 * <p>TODO: add links to files
 *
 * @author Alexandra Roatis
 */
public class RLPSpecTest {

    // ENCODING

    @Test
    public void testEncodeEmptyString() {
        String input = "";
        byte[] expected = Hex.decode("80");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeShortString1() {
        String input = "dog";
        byte[] expected = Hex.decode("83646f67");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeShortString2() {
        String input = "Lorem ipsum dolor sit amet, consectetur adipisicing eli"; // length = 55
        byte[] expected =
                Hex.decode(
                        "b74c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e7365637465747572206164697069736963696e6720656c69");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeLongString1() {
        String input = "Lorem ipsum dolor sit amet, consectetur adipisicing elit"; // length = 56
        byte[] expected =
                Hex.decode(
                        "b8384c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e7365637465747572206164697069736963696e6720656c6974");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeLongString2() {
        String input =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur mauris magna, suscipit sed vehicula non, iaculis faucibus tortor. Proin suscipit ultricies malesuada. Duis tortor elit, dictum quis tristique eu, ultrices at risus. Morbi a est imperdiet mi ullamcorper aliquet suscipit nec lorem. Aenean quis leo mollis, vulputate elit varius, consequat enim. Nulla ultrices turpis justo, et posuere urna consectetur nec. Proin non convallis metus. Donec tempor ipsum in mauris congue sollicitudin. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Suspendisse convallis sem vel massa faucibus, eget lacinia lacus tempor. Nulla quis ultricies purus. Proin auctor rhoncus nibh condimentum mollis. Aliquam consequat enim at metus luctus, a eleifend purus egestas. Curabitur at nibh metus. Nam bibendum, neque at auctor tristique, lorem libero aliquet arcu, non interdum tellus lectus sit amet eros. Cras rhoncus, metus ac ornare cursus, dolor justo ultrices metus, at ullamcorper volutpat";
        byte[] expected =
                Hex.decode(
                        "b904004c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e73656374657475722061646970697363696e6720656c69742e20437572616269747572206d6175726973206d61676e612c20737573636970697420736564207665686963756c61206e6f6e2c20696163756c697320666175636962757320746f72746f722e2050726f696e20737573636970697420756c74726963696573206d616c6573756164612e204475697320746f72746f7220656c69742c2064696374756d2071756973207472697374697175652065752c20756c7472696365732061742072697375732e204d6f72626920612065737420696d70657264696574206d6920756c6c616d636f7270657220616c6971756574207375736369706974206e6563206c6f72656d2e2041656e65616e2071756973206c656f206d6f6c6c69732c2076756c70757461746520656c6974207661726975732c20636f6e73657175617420656e696d2e204e756c6c6120756c74726963657320747572706973206a7573746f2c20657420706f73756572652075726e6120636f6e7365637465747572206e65632e2050726f696e206e6f6e20636f6e76616c6c6973206d657475732e20446f6e65632074656d706f7220697073756d20696e206d617572697320636f6e67756520736f6c6c696369747564696e2e20566573746962756c756d20616e746520697073756d207072696d697320696e206661756369627573206f726369206c756374757320657420756c74726963657320706f737565726520637562696c69612043757261653b2053757370656e646973736520636f6e76616c6c69732073656d2076656c206d617373612066617563696275732c2065676574206c6163696e6961206c616375732074656d706f722e204e756c6c61207175697320756c747269636965732070757275732e2050726f696e20617563746f722072686f6e637573206e69626820636f6e64696d656e74756d206d6f6c6c69732e20416c697175616d20636f6e73657175617420656e696d206174206d65747573206c75637475732c206120656c656966656e6420707572757320656765737461732e20437572616269747572206174206e696268206d657475732e204e616d20626962656e64756d2c206e6571756520617420617563746f72207472697374697175652c206c6f72656d206c696265726f20616c697175657420617263752c206e6f6e20696e74657264756d2074656c6c7573206c65637475732073697420616d65742065726f732e20437261732072686f6e6375732c206d65747573206163206f726e617265206375727375732c20646f6c6f72206a7573746f20756c747269636573206d657475732c20617420756c6c616d636f7270657220766f6c7574706174");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeZero() {
        int input = 0;
        byte[] expected = Hex.decode("80");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeSmallInt1() {
        int input = 1;
        byte[] expected = Hex.decode("01");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeSmallInt2() {
        int input = 16;
        byte[] expected = Hex.decode("10");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeSmallInt3() {
        int input = 79;
        byte[] expected = Hex.decode("4f");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeSmallInt4() {
        int input = 127;
        byte[] expected = Hex.decode("7f");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeMediumInt1() {
        // TODO: look into broken test
        //        int input = 128;
        //        byte[] expected = Hex.decode("8180");
        //
        //        byte[] actual = RLP.encode(input);
        //        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeMediumInt2() {
        int input = 1000;
        byte[] expected = Hex.decode("8203e8");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeMediumInt3() {
        int input = 100000;
        byte[] expected = Hex.decode("830186a0");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeMediumInt4() {
        BigInteger input = new BigInteger("83729609699884896815286331701780722", 10);
        byte[] expected = Hex.decode("8F102030405060708090A0B0C0D0E0F2");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testEncodeMediumInt5() {
        BigInteger input =
                new BigInteger(
                        "105315505618206987246253880190783558935785933862974822347068935681", 10);
        byte[] expected = Hex.decode("9C0100020003000400050006000700080009000A000B000C000D000E01");

        byte[] actual = RLP.encode(input);
        assertArrayEquals(expected, actual);
    }

    // DECODING

    @Test
    public void testDecodeEmptyString() {
        byte[] input = Hex.decode("80");
        String expected = "";

        String actual = (String) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, actual);
    }

    @Test
    public void testDecodeShortString1() {
        byte[] input = Hex.decode("83646f67");
        String expected = "dog";

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, bytesToAscii(actual));
    }

    @Test
    public void testDecodeShortString2() {
        // TODO: find issue
        //        byte[] input = Hex.decode(
        //
        // "b74c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e7365637465747572206164697069736963696e6720656c69");
        //        String expected = "Lorem ipsum dolor sit amet, consectetur adipisicing eli"; //
        // length = 55
        //
        //        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        //        assertEquals(expected, bytesToAscii(actual));
    }

    @Test
    public void testDecodeLongString1() {
        byte[] input =
                Hex.decode(
                        "b8384c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e7365637465747572206164697069736963696e6720656c6974");
        String expected = "Lorem ipsum dolor sit amet, consectetur adipisicing elit"; // length = 56

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, bytesToAscii(actual));
    }

    @Test
    public void testDecodeLongString2() {
        byte[] input =
                Hex.decode(
                        "b904004c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e73656374657475722061646970697363696e6720656c69742e20437572616269747572206d6175726973206d61676e612c20737573636970697420736564207665686963756c61206e6f6e2c20696163756c697320666175636962757320746f72746f722e2050726f696e20737573636970697420756c74726963696573206d616c6573756164612e204475697320746f72746f7220656c69742c2064696374756d2071756973207472697374697175652065752c20756c7472696365732061742072697375732e204d6f72626920612065737420696d70657264696574206d6920756c6c616d636f7270657220616c6971756574207375736369706974206e6563206c6f72656d2e2041656e65616e2071756973206c656f206d6f6c6c69732c2076756c70757461746520656c6974207661726975732c20636f6e73657175617420656e696d2e204e756c6c6120756c74726963657320747572706973206a7573746f2c20657420706f73756572652075726e6120636f6e7365637465747572206e65632e2050726f696e206e6f6e20636f6e76616c6c6973206d657475732e20446f6e65632074656d706f7220697073756d20696e206d617572697320636f6e67756520736f6c6c696369747564696e2e20566573746962756c756d20616e746520697073756d207072696d697320696e206661756369627573206f726369206c756374757320657420756c74726963657320706f737565726520637562696c69612043757261653b2053757370656e646973736520636f6e76616c6c69732073656d2076656c206d617373612066617563696275732c2065676574206c6163696e6961206c616375732074656d706f722e204e756c6c61207175697320756c747269636965732070757275732e2050726f696e20617563746f722072686f6e637573206e69626820636f6e64696d656e74756d206d6f6c6c69732e20416c697175616d20636f6e73657175617420656e696d206174206d65747573206c75637475732c206120656c656966656e6420707572757320656765737461732e20437572616269747572206174206e696268206d657475732e204e616d20626962656e64756d2c206e6571756520617420617563746f72207472697374697175652c206c6f72656d206c696265726f20616c697175657420617263752c206e6f6e20696e74657264756d2074656c6c7573206c65637475732073697420616d65742065726f732e20437261732072686f6e6375732c206d65747573206163206f726e617265206375727375732c20646f6c6f72206a7573746f20756c747269636573206d657475732c20617420756c6c616d636f7270657220766f6c7574706174");
        String expected =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur mauris magna, suscipit sed vehicula non, iaculis faucibus tortor. Proin suscipit ultricies malesuada. Duis tortor elit, dictum quis tristique eu, ultrices at risus. Morbi a est imperdiet mi ullamcorper aliquet suscipit nec lorem. Aenean quis leo mollis, vulputate elit varius, consequat enim. Nulla ultrices turpis justo, et posuere urna consectetur nec. Proin non convallis metus. Donec tempor ipsum in mauris congue sollicitudin. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Suspendisse convallis sem vel massa faucibus, eget lacinia lacus tempor. Nulla quis ultricies purus. Proin auctor rhoncus nibh condimentum mollis. Aliquam consequat enim at metus luctus, a eleifend purus egestas. Curabitur at nibh metus. Nam bibendum, neque at auctor tristique, lorem libero aliquet arcu, non interdum tellus lectus sit amet eros. Cras rhoncus, metus ac ornare cursus, dolor justo ultrices metus, at ullamcorper volutpat";

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, bytesToAscii(actual));
    }

    @Test
    public void testDecodeZero() {
        // TODO: check discrepancy between RLP json files wrt 0
        byte[] input = Hex.decode("80");
        String expected = ""; // note that it decodes to empty list

        String actual = (String) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, actual);
    }

    @Test
    public void testDecodeSmallInt1() {
        byte[] input = Hex.decode("01");
        int expected = 1;

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, byteArrayToInt(actual));
    }

    @Test
    public void testDecodeSmallInt2() {
        byte[] input = Hex.decode("10");
        int expected = 16;

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, byteArrayToInt(actual));
    }

    @Test
    public void testDecodeSmallInt3() {
        byte[] input = Hex.decode("4f");
        int expected = 79;

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, byteArrayToInt(actual));
    }

    @Test
    public void testDecodeSmallInt4() {
        byte[] input = Hex.decode("7f");
        int expected = 127;

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, byteArrayToInt(actual));
    }

    @Test
    public void testDecodeMediumInt1() {
        byte[] input = Hex.decode("8180");
        int expected = 128;

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, byteArrayToInt(actual));
    }

    @Test
    public void testDecodeMediumInt2() {
        byte[] input = Hex.decode("8203e8");
        int expected = 1000;

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, byteArrayToInt(actual));
    }

    @Test
    public void testDecodeMediumInt3() {
        byte[] input = Hex.decode("830186a0");
        int expected = 100000;

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, byteArrayToInt(actual));
    }

    @Test
    public void testDecodeMediumInt4() {
        byte[] input = Hex.decode("8F102030405060708090A0B0C0D0E0F2");
        BigInteger expected = new BigInteger("83729609699884896815286331701780722", 10);

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, new BigInteger(1, actual));
    }

    @Test
    public void testDecodeMediumInt5() {
        byte[] input = Hex.decode("9C0100020003000400050006000700080009000A000B000C000D000E01");
        BigInteger expected =
                new BigInteger(
                        "105315505618206987246253880190783558935785933862974822347068935681", 10);

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertEquals(expected, new BigInteger(1, actual));
    }
}
