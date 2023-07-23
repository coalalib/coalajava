package com.ndmsystems.coala.message

import com.ndmsystems.coala.helpers.logging.LogHelper.e
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

class CoAPMessageOption : Comparable<CoAPMessageOption> {
    @JvmField
    val code: CoAPMessageOptionCode
    @JvmField
    var value: Any? = null

    constructor(code: CoAPMessageOptionCode, value: Any) {
        this.code = code
        this.value = value
    }

    constructor(code: CoAPMessageOptionCode, value: ByteArray) {
        this.code = code
        fromBytes(value)
    }

    val isRepeatable: Boolean
        get() = when (this.code) {
            CoAPMessageOptionCode.OptionURIPath, CoAPMessageOptionCode.OptionURIQuery, CoAPMessageOptionCode.OptionLocationPath, CoAPMessageOptionCode.OptionLocationQuery, CoAPMessageOptionCode.OptionIfMatch, CoAPMessageOptionCode.OptionEtag -> true
            else -> false
        }

    override fun compareTo(other: CoAPMessageOption): Int {
        return code.value.compareTo(other.code.value)
    }

    private fun fromBytes(data: ByteArray) {
        when (this.code) {
            CoAPMessageOptionCode.OptionBlock1, CoAPMessageOptionCode.OptionBlock2, CoAPMessageOptionCode.OptionURIPort, CoAPMessageOptionCode.OptionContentFormat, CoAPMessageOptionCode.OptionMaxAge, CoAPMessageOptionCode.OptionAccept, CoAPMessageOptionCode.OptionSize1, CoAPMessageOptionCode.OptionSize2, CoAPMessageOptionCode.OptionHandshakeType, CoAPMessageOptionCode.OptionObserve, CoAPMessageOptionCode.OptionSessionNotFound, CoAPMessageOptionCode.OptionSessionExpired, CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, CoAPMessageOptionCode.OptionURIScheme -> if (data.size > 4) value =
                ByteBuffer.wrap(data).int else {
                val bigData = ByteArray(4)
                var i = 0
                while (i < data.size) {
                    bigData[3 - data.size + i + 1] = data[i]
                    i++
                }
                value = ByteBuffer.wrap(bigData).int
            }

            CoAPMessageOptionCode.OptionProxySecurityID -> if (data.size > 4) value = ByteBuffer.wrap(data).long else {
                val buffer = ByteBuffer.allocate(8).put(byteArrayOf(0, 0, 0, 0)).put(data)
                buffer.position(0)
                value = buffer.long
            }

            CoAPMessageOptionCode.OptionURIHost, CoAPMessageOptionCode.OptionEtag, CoAPMessageOptionCode.OptionLocationPath, CoAPMessageOptionCode.OptionURIPath, CoAPMessageOptionCode.OptionURIQuery, CoAPMessageOptionCode.OptionLocationQuery, CoAPMessageOptionCode.OptionProxyScheme, CoAPMessageOptionCode.OptionProxyURI -> value =
                String(data)

            CoAPMessageOptionCode.OptionCookie, CoAPMessageOptionCode.OptionCoapsURI -> value = data
            else -> {
                e("Try from byte unknown option: " + this.code)
                value = data
            }
        }
    }

    val maxSizeInBytes: Int
        get() = when (this.code) {
            CoAPMessageOptionCode.OptionBlock1, CoAPMessageOptionCode.OptionBlock2 -> 3
            CoAPMessageOptionCode.OptionURIScheme -> 1
            else -> Int.MAX_VALUE
        }

    fun toBytes(): ByteArray {
        if (value != null) {

            //is it long
            if (code == CoAPMessageOptionCode.OptionProxySecurityID) try {
                val bytes = ByteArray(8)
                ByteBuffer.wrap(bytes).putLong(value as Long)
                return Arrays.copyOfRange(bytes, 4, 8)
            } catch (e: ClassCastException) {
            }

            // Is it Integer?
            // @hbz
            try {
                return toByteArray(value as Int)
            } catch (e: ClassCastException) {
            }

            // Is it String?
            try {
                val stringValue = value as String
                return stringValue.toByteArray(StandardCharsets.UTF_8)
            } catch (e: ClassCastException) {
            }
            try {
                return value as ByteArray
            } catch (e: ClassCastException) {
            }
        }

        // can't recognize the value type...
        return ByteArray(0)
    }

    private fun toByteArray(number: Int): ByteArray {
        if (number ushr 24 != 0) return byteArrayOf((number ushr 24).toByte(), (number ushr 16).toByte(), (number ushr 8).toByte(), number.toByte())
        if (number ushr 16 != 0) return byteArrayOf((number ushr 16).toByte(), (number ushr 8).toByte(), number.toByte())
        return if (number ushr 8 != 0) byteArrayOf((number ushr 8).toByte(), number.toByte()) else byteArrayOf(number.toByte())
    }
}