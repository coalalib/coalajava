package com.ndmsystems.coala.crypto;

import android.support.test.runner.AndroidJUnit4;

import com.ndmsystems.coala.BaseTest;
import com.ndmsystems.coala.helpers.Hex;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Created by bas on 01.11.16.
 */
@RunWith(AndroidJUnit4.class)
public class AesGcmTest extends BaseTest {
    private static final byte[] KEY = Hex.decodeHex("7CF5919725AD47A9873A2E449984CB4D".toCharArray());
    private static final byte[] MESSAGE = "The quick, brown fox jumps over a lazy dog.".getBytes();
    private static final byte[] ANOTHER_MESSAGE = "Hello, world!".getBytes();
    private static final byte[] NONCE = Hex.decodeHex("139B03C8CA7973584969EE50".toCharArray());
    private static final byte[] AAD = Hex.decodeHex("F0D859E9".toCharArray());

    @Test
    public void testSimpleEncryptDecrypt() {
        AesGcm aesGcm = new AesGcm(KEY);
        byte [] encryptedMessage = null;
        try {
            encryptedMessage = aesGcm.seal(MESSAGE, NONCE, AAD);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }

        try {
            Assert.assertEquals(new String(MESSAGE), new String(aesGcm.open(encryptedMessage, NONCE, AAD)));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testEqualsEncrypted() {
        AesGcm aesGcm = new AesGcm(KEY);
        byte [] encryptedMessage1 = null;
        byte [] encryptedMessage2 = null;
        try {
            encryptedMessage1 = aesGcm.seal(MESSAGE, NONCE, AAD);
            encryptedMessage2 = aesGcm.seal(MESSAGE, NONCE, AAD);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }

        try {
            Assert.assertArrayEquals(encryptedMessage1, encryptedMessage2);
            Assert.assertEquals(new String(aesGcm.open(encryptedMessage1, NONCE, AAD)), new String(aesGcm.open(encryptedMessage2, NONCE, AAD)));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testNotEquals() {
        AesGcm aesGcm = new AesGcm(KEY);
        byte [] encryptedMessage1 = null;
        byte [] encryptedMessage2 = null;
        try {
            encryptedMessage1 = aesGcm.seal(MESSAGE, NONCE, AAD);
            encryptedMessage2 = aesGcm.seal(ANOTHER_MESSAGE, NONCE, AAD);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }

        try {
            Assert.assertNotEquals(new String(aesGcm.open(encryptedMessage1, NONCE, AAD)), new String(aesGcm.open(encryptedMessage2, NONCE, AAD)));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    @Test
    public void testWithTrueData() {
        AesGcm aesGcm = new AesGcm(KEY);
        byte [] encryptedMessage = null;
        try {
            encryptedMessage = aesGcm.seal(MESSAGE, NONCE, AAD);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }

        try {
            Assert.assertArrayEquals(encryptedMessage, Hex.decodeHex("462cc3bbaf3798940a07f25e056d8a34a49be521fdf709f2ab5ef987aa7b924520fdf125a9473b35c5d2f2cb32e50c83d72cbdb451d6c1".toCharArray()));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
}
