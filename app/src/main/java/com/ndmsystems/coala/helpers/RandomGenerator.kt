package com.ndmsystems.coala.helpers

import java.security.SecureRandom

object RandomGenerator {
    private const val MAX_UINT = 4294967296L
    private val random = SecureRandom()
    @JvmStatic
    fun getRandom(size: Int): ByteArray {
        val b = ByteArray(size)
        random.nextBytes(b)
        return b
    }

    val randomUnsignedIntAsLong: Long
        // SecureRandom has no bounded nextLong; mask 64 random bits down to an
        // unsigned 32-bit value, i.e. a uniform draw from [0, MAX_UINT).
        get() = random.nextLong() and (MAX_UINT - 1)
}