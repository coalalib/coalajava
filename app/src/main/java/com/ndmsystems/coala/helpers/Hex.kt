package com.ndmsystems.coala.helpers

/**
 * Created by bas on 25.10.16.
 */
object Hex {
    private val DIGITS_LOWER: CharArray
    private val DIGITS_UPPER: CharArray
    @JvmStatic
    fun decodeHex(data: CharArray): ByteArray {
        val len = data.size
        val out = ByteArray(len shr 1)
        var i = 0
        var j = 0
        while (j < len) {
            var f = toDigit(data[j], j) shl 4
            ++j
            f = f or toDigit(data[j], j)
            ++j
            out[i] = (f and 255).toByte()
            ++i
        }
        return out
    }

    internal fun toDigit(ch: Char, index: Int): Int {
        return ch.digitToIntOrNull(16) ?: -1
    }

    @JvmOverloads
    fun encodeHex(data: ByteArray, toLowerCase: Boolean = true): CharArray {
        return encodeHex(data, if (toLowerCase) DIGITS_LOWER else DIGITS_UPPER)
    }

    internal fun encodeHex(data: ByteArray, toDigits: CharArray): CharArray {
        val l = data.size
        val out = CharArray(l shl 1)
        var i = 0
        var j = 0
        while (i < l) {
            out[j++] = toDigits[240 and data[i].toInt() ushr 4]
            out[j++] = toDigits[15 and data[i].toInt()]
            ++i
        }
        return out
    }

    @JvmStatic
    fun encodeHexString(data: ByteArray?): String {
        return if (data == null) "" else String(encodeHex(data))
    }

    init {
        DIGITS_LOWER = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
        DIGITS_UPPER = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
    }
}