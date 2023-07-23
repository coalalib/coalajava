package com.ndmsystems.coala.helpers

import java.util.Random
import java.util.concurrent.ThreadLocalRandom

/**
 * Created by bas on 15.11.16.
 */
object RandomGenerator {
    private const val MAX_UINT = 4294967296L
    private val random = Random()
    @JvmStatic
    fun getRandom(size: Int): ByteArray {
        val b = ByteArray(size)
        random.nextBytes(b)
        return b
    }

    val randomUnsignedIntAsLong: Long
        get() = ThreadLocalRandom.current().nextLong(0, MAX_UINT)
}