package com.ndmsystems.coala.crypto

import com.ndmsystems.coala.helpers.Hex.decodeHex
import com.ndmsystems.coala.helpers.Hex.encodeHexString
import com.ndmsystems.coala.helpers.logging.LogHelper
import org.junit.Assert
import org.junit.Test

/**
 * Created by bas on 02.11.16.
 */
class HkdfTest {
    @Test
    fun testCase1() {
        LogHelper.setLogLevel(LogHelper.LogLevel.WARNING)
        val IKM = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"
        val salt = "000102030405060708090a0b0c"
        val info = "f0f1f2f3f4f5f6f7f8f9"
        val OKM = "3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b88718"
        val peerKey = "3cb25f25faacd57a90434f64d0362f2a"
        val myKey = "2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
        val peerIV = "34007208"
        val myIV = "d5b88718"
        val hkdf = Hkdf(decodeHex(IKM.toCharArray()), decodeHex(salt.toCharArray()), decodeHex(info.toCharArray()))
        Assert.assertEquals(hkdf.toString(), OKM)
        Assert.assertEquals(encodeHexString(hkdf.peerKey), peerKey)
        Assert.assertEquals(encodeHexString(hkdf.myKey), myKey)
        Assert.assertEquals(encodeHexString(hkdf.peerIV), peerIV)
        Assert.assertEquals(encodeHexString(hkdf.myIV), myIV)
    }

    @Test
    fun testCase2() {
        LogHelper.setLogLevel(LogHelper.LogLevel.WARNING)
        val IKM = "000102030405060708090a0b0c0d0e0f" +
                "101112131415161718191a1b1c1d1e1f" +
                "202122232425262728292a2b2c2d2e2f" +
                "303132333435363738393a3b3c3d3e3f" +
                "404142434445464748494a4b4c4d4e4f"
        val salt = "606162636465666768696a6b6c6d6e6f" +
                "707172737475767778797a7b7c7d7e7f" +
                "808182838485868788898a8b8c8d8e8f" +
                "909192939495969798999a9b9c9d9e9f" +
                "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"
        val info = "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
                "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf" +
                "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
                "e0e1e2e3e4e5e6e7e8e9eaebecedeeef" +
                "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"
        val OKM = "b11e398dc80327a1c8e7f78c596a4934" +
                "4f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac78272"
        val peerKey = "b11e398dc80327a1c8e7f78c596a4934"
        val myKey = "4f012eda2d4efad8a050cc4c19afa97c"
        val peerIV = "59045a99"
        val myIV = "cac78272"
        val hkdf = Hkdf(decodeHex(IKM.toCharArray()), decodeHex(salt.toCharArray()), decodeHex(info.toCharArray()))
        Assert.assertEquals(hkdf.toString(), OKM)
        Assert.assertEquals(encodeHexString(hkdf.peerKey), peerKey)
        Assert.assertEquals(encodeHexString(hkdf.myKey), myKey)
        Assert.assertEquals(encodeHexString(hkdf.peerIV), peerIV)
        Assert.assertEquals(encodeHexString(hkdf.myIV), myIV)
    }

    @Test
    fun testCase3() {
        LogHelper.setLogLevel(LogHelper.LogLevel.WARNING)
        val IKM = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"
        val salt = ""
        val info = ""
        val OKM = "8da4e775a563c18f715f802a063c5a31" +
                "b8a11f5c5ee1879ec3454e5f3c738d2d" +
                "9d201395faa4b61a"
        val peerKey = "8da4e775a563c18f715f802a063c5a31"
        val myKey = "b8a11f5c5ee1879ec3454e5f3c738d2d"
        val peerIV = "9d201395"
        val myIV = "faa4b61a"
        val hkdf = Hkdf(decodeHex(IKM.toCharArray()), decodeHex(salt.toCharArray()), decodeHex(info.toCharArray()))
        Assert.assertEquals(hkdf.toString(), OKM)
        Assert.assertEquals(encodeHexString(hkdf.peerKey), peerKey)
        Assert.assertEquals(encodeHexString(hkdf.myKey), myKey)
        Assert.assertEquals(encodeHexString(hkdf.peerIV), peerIV)
        Assert.assertEquals(encodeHexString(hkdf.myIV), myIV)
    }
}