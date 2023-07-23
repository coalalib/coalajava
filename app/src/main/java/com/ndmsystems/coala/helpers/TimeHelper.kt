package com.ndmsystems.coala.helpers

object TimeHelper {
    @JvmStatic
    val timeForMeasurementInMilliseconds: Long
        get() = System.nanoTime() / 1000000
}