package com.ndmsystems.coala.helpers

/**
 * Created by bas on 18.10.16.
 */
object StringHelper {
    @JvmStatic
    fun join(list: List<String?>, delim: String?): String {
        val sb = StringBuilder()
        var loopDelim: String? = ""
        for (s in list) {
            sb.append(loopDelim)
            sb.append(s)
            loopDelim = delim
        }
        return sb.toString()
    }

    private const val UNIT = 1024
    fun getHumanReadableByteString(bytes: Long): String {
        return getHumanReadableBitOrByteString(bytes, true)
    }

    fun getHumanReadableBitString(bytes: Long): String {
        return getHumanReadableBitOrByteString(bytes, false)
    }

    private fun getHumanReadableBitOrByteString(bytes: Long, isBytes: Boolean): String {
        if (bytes < UNIT) return String.format(if (isBytes) "%d bytes" else "%d bits", bytes)
        val exp = (Math.log(bytes.toDouble()) / Math.log(UNIT.toDouble())).toInt()
        val pre = "kMGTPE"[exp - 1].toString() + ""
        return String.format(if (isBytes) "%.1f %sbytes" else "%.1f %sbits", bytes / Math.pow(UNIT.toDouble(), exp.toDouble()), pre)
    }
}