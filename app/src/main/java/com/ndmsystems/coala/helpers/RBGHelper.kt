package com.ndmsystems.coala.helpers

import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

/**
 * Random Bytes Generator
 */
object RBGHelper {
    @JvmStatic
    fun rbg(length: Int): ByteArray {
        val random = ByteArray(length)
        try {
            val secureRandom = SecureRandom.getInstance("SHA1PRNG")
            secureRandom.nextBytes(random)
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        }
        return random
    }
}