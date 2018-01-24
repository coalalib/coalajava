package com.ndmsystems.coala.crypto;

import com.ndmsystems.coala.helpers.Hex;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by bas on 02.11.16.
 */

public class HkdfTest {
    @Test
    public void testCase1() {

        String IKM =    "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b";
        String salt =   "000102030405060708090a0b0c";
        String info =   "f0f1f2f3f4f5f6f7f8f9";


        String OKM =    "3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b88718";
        String peerKey =    "3cb25f25faacd57a90434f64d0362f2a";
        String myKey =    "2d2d0a90cf1a5a4c5db02d56ecc4c5bf";
        String peerIV =    "34007208";
        String myIV =    "d5b88718";

        Hkdf hkdf = new Hkdf(Hex.decodeHex(IKM.toCharArray()), Hex.decodeHex(salt.toCharArray()), Hex.decodeHex(info.toCharArray()));
        assertEquals(hkdf.toString(), OKM);
        assertEquals(Hex.encodeHexString(hkdf.peerKey), peerKey);
        assertEquals(Hex.encodeHexString(hkdf.myKey), myKey);
        assertEquals(Hex.encodeHexString(hkdf.peerIV), peerIV);
        assertEquals(Hex.encodeHexString(hkdf.myIV), myIV);
    }

    @Test
    public void testCase2() {

        String IKM =    "000102030405060708090a0b0c0d0e0f" +
                "101112131415161718191a1b1c1d1e1f" +
                "202122232425262728292a2b2c2d2e2f" +
                "303132333435363738393a3b3c3d3e3f" +
                "404142434445464748494a4b4c4d4e4f";
        String salt =   "606162636465666768696a6b6c6d6e6f" +
                "707172737475767778797a7b7c7d7e7f" +
                "808182838485868788898a8b8c8d8e8f" +
                "909192939495969798999a9b9c9d9e9f" +
                "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf";
        String info =   "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
                "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf" +
                "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
                "e0e1e2e3e4e5e6e7e8e9eaebecedeeef" +
                "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff";

        String OKM =    "b11e398dc80327a1c8e7f78c596a4934" +
                "4f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac78272";
        String peerKey =    "b11e398dc80327a1c8e7f78c596a4934";
        String myKey =    "4f012eda2d4efad8a050cc4c19afa97c";
        String peerIV =    "59045a99";
        String myIV =    "cac78272";

        Hkdf hkdf = new Hkdf(Hex.decodeHex(IKM.toCharArray()), Hex.decodeHex(salt.toCharArray()), Hex.decodeHex(info.toCharArray()));
        assertEquals(hkdf.toString(), OKM);
        assertEquals(Hex.encodeHexString(hkdf.peerKey), peerKey);
        assertEquals(Hex.encodeHexString(hkdf.myKey), myKey);
        assertEquals(Hex.encodeHexString(hkdf.peerIV), peerIV);
        assertEquals(Hex.encodeHexString(hkdf.myIV), myIV);
    }

    @Test
    public void testCase3() {

        String IKM =    "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b";
        String salt =   "";
        String info =   "";
        String OKM =    "8da4e775a563c18f715f802a063c5a31" +
                "b8a11f5c5ee1879ec3454e5f3c738d2d" +
                "9d201395faa4b61a";
        String peerKey =    "8da4e775a563c18f715f802a063c5a31";
        String myKey =    "b8a11f5c5ee1879ec3454e5f3c738d2d";
        String peerIV =    "9d201395";
        String myIV =    "faa4b61a";

        Hkdf hkdf = new Hkdf(Hex.decodeHex(IKM.toCharArray()), Hex.decodeHex(salt.toCharArray()), Hex.decodeHex(info.toCharArray()));
        assertEquals(hkdf.toString(), OKM);
        assertEquals(Hex.encodeHexString(hkdf.peerKey), peerKey);
        assertEquals(Hex.encodeHexString(hkdf.myKey), myKey);
        assertEquals(Hex.encodeHexString(hkdf.peerIV), peerIV);
        assertEquals(Hex.encodeHexString(hkdf.myIV), myIV);
    }
}
