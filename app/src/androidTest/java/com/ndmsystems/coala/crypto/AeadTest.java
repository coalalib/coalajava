package com.ndmsystems.coala.crypto;


import androidx.test.runner.AndroidJUnit4;

import com.ndmsystems.coala.BaseTest;
import com.ndmsystems.coala.helpers.Hex;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

/**
 * Created by bas on 03.11.16.
 */
@RunWith(AndroidJUnit4.class)
public class AeadTest extends BaseTest{

    private static final byte[] KEY = Hex.decodeHex("7CF5919725AD47A9873A2E449984CB4D".toCharArray());
    private static final byte[] MESSAGE = "The quick, brown fox jumps over a lazy dog.".getBytes();
    private static final byte[] AAD = Hex.decodeHex("F0D859E9".toCharArray());

    private static final byte[] peerKey =   Hex.decodeHex("bdd1cf3e4a5d0d1c009be633da60a372".toCharArray());
    private static final byte[] myKey =     Hex.decodeHex("6e486ac093054578dc5308b966b9ff28".toCharArray());
    private static final byte[] peerIV =    Hex.decodeHex("799212a9".toCharArray());
    private static final byte[] myIV =      Hex.decodeHex("b3efe5ce".toCharArray());

    private static final Aead myAead = new Aead(peerKey, myKey, peerIV, myIV);
    private static final Aead peerAead = new Aead(myKey, peerKey, myIV, peerIV);

    @Test
    public void testSimpleEncryptDecrypt() {
        Aead aead = new Aead(KEY, KEY, new byte[4], new byte[4]);
        byte [] encryptedMessage = null;
        try {
            encryptedMessage = aead.encrypt(MESSAGE, 1, AAD);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }

        Assert.assertEquals(new String(MESSAGE), new String(aead.decrypt(encryptedMessage, 1, AAD)));
        assertThat(MESSAGE, not(equalTo(aead.decrypt(encryptedMessage, 2, AAD))));
    }

    @Test
    public void testSeal() {
        byte[] plainText = "The quick, brown fox jumps over a lazy dog.".getBytes();
        byte[] aData = Hex.decodeHex("88e564a2e6b64a356efd11".toCharArray());

        byte[] data = myAead.encrypt(plainText, 300, aData);
        byte[] expectedData = Hex.decodeHex(("066770c836b2c0a745adaeef33005392a6dd02c85a5047149a051dfb6d" +
                "d15f840083c407154e04f76d878cb42973e72f4c3e10b9a67cf3").toCharArray());
        Assert.assertArrayEquals(data, expectedData);
    }

    @Test
    public void testOpen() {
        byte[] cipherText = Hex.decodeHex(("1616888d96446e598e31fb3dafe855018bddf93cca9401f42fed6d19dc49ef4c" +
                "f816dddd741ccf2af09eeecbd3f867982e2a602d67cc78").toCharArray());
        byte[] aData = Hex.decodeHex("88e564a2e6b64a356efd11".toCharArray());

        byte[] data = myAead.decrypt(cipherText, 400, aData);
        Assert.assertArrayEquals(data, MESSAGE);
    }

    @Test
    public void testDuplex() {
        byte[] myCipher = myAead.encrypt(MESSAGE, 500, null);
        byte[] peerPlain = peerAead.decrypt(myCipher, 500, null);
        Assert.assertArrayEquals(MESSAGE, peerPlain);

        byte[] peerCipher = peerAead.encrypt(MESSAGE, 500, null);
        byte[] myPlain = myAead.decrypt(peerCipher, 500, null);
        Assert.assertArrayEquals(MESSAGE, myPlain);
        assertThat(myCipher, not(equalTo(peerCipher)));
    }

    /*@Test
    public void testMakeNonce() {
        byte[] iv = new byte[4];
        int counter = 0;

    }

    @Test
    public void testMakeNonce2() {
        byte[] iv = new byte[4];
        Assert.assertArrayEquals(new Aead().makeNonce(iv, 1), new byte[] {0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0});
        Assert.assertArrayEquals(new Aead().makeNonce(iv, 256), new byte[] {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0});
        Assert.assertArrayEquals(new Aead().makeNonce(iv, 65535), new byte[] {0, 0, 0, 0, -1, -1, 0, 0, 0, 0, 0, 0});
    }

    @Test
    public void testMakeNonce3() {
        byte[] iv = new byte[] {1, 2, 3, 4};
        Assert.assertArrayEquals(new Aead().makeNonce(iv, 1), new byte[] {1, 2, 3, 4, 1, 0, 0, 0, 0, 0, 0, 0});
        Assert.assertArrayEquals(new Aead().makeNonce(iv, 256), new byte[] {1, 2, 3, 4, 0, 1, 0, 0, 0, 0, 0, 0});
        Assert.assertArrayEquals(new Aead().makeNonce(iv, 65535), new byte[] {1, 2, 3, 4, -1, -1, 0, 0, 0, 0, 0, 0});
    }*/
}
