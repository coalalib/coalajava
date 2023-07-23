package com.ndmsystems.coala.layers.arq

import com.ndmsystems.coala.helpers.logging.LogHelper.e
import kotlin.math.min

/**
 * Created by Владимир on 16.08.2017.
 */
class SlidingWindow<T> @JvmOverloads constructor(size: Int, var offset: Int = 0) {
    private var values: Array<Any?>
    private var size = 0

    init {
        values = arrayOfNulls(size)
    }

    fun getSize(): Int {
        return values.size
    }

    operator fun set(number: Int, value: T) {
        val windowIndex = number - offset
        if (windowIndex > values.size - 1) {
            e("ARQ: window index out of bounds")
            return
        } else if (windowIndex < 0) return
        values[windowIndex] = value
    }

    fun advance(): T? {
        val firstBlock = values[0] as T? ?: return null
        System.arraycopy(values, 1, values, 0, values.size - 1)
        values[values.size - 1] = null
        offset++
        return firstBlock
    }

    fun getValue(windowIndex: Int): T? {
        return values[windowIndex] as T?
    }

    fun tail(): Int {
        return offset + values.size - 1
    }

    fun setSize(windowSize: Int) {
        val tempValues = arrayOfNulls<Any>(windowSize)
        System.arraycopy(values, 0, tempValues, 0, min(windowSize, size))
        values = tempValues
        size = windowSize
    }
}