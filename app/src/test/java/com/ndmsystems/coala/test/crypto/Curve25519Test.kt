package com.ndmsystems.coala.test.crypto

import com.ndmsystems.coala.crypto.Curve25519
import org.junit.Assert
import org.junit.Test

/**
 * Created by bas on 31.10.16.
 */
class Curve25519Test {
    @Test
    fun curveTest() {
        val first = Curve25519()
        val second = Curve25519()
        val sharedSecretFirst = first.generateSharedSecret(second.publicKey)
        val sharedSecretSecond = second.generateSharedSecret(first.publicKey)
        Assert.assertEquals(sharedSecretFirst.size.toLong(), 32)
        Assert.assertEquals(sharedSecretSecond.size.toLong(), 32)
        Assert.assertNotEquals(first.publicKey, second.publicKey)
        Assert.assertArrayEquals(sharedSecretFirst, sharedSecretSecond)
    }
}