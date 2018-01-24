package com.ndmsystems.coala.test.crypto;

import com.ndmsystems.coala.crypto.Curve25519;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by bas on 31.10.16.
 */
public class Curve25519Test {

    @Test
    public void curveTest() {
        Curve25519 first = new Curve25519();
        Curve25519 second = new Curve25519();

        byte[] sharedSecretFirst = first.generateSharedSecret(second.getPublicKey());
        byte[] sharedSecretSecond = second.generateSharedSecret(first.getPublicKey());

        Assert.assertEquals(sharedSecretFirst.length, 32);
        Assert.assertEquals(sharedSecretSecond.length, 32);
        Assert.assertNotEquals(first.getPublicKey(), second.getPublicKey());

        Assert.assertArrayEquals(sharedSecretFirst, sharedSecretSecond);
    }
}
