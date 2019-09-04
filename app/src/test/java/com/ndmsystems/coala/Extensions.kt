package com.ndmsystems.coala



/*
 * Created by Evgenii Stepanov on 05.09.19
 */

fun Int.to16BitUnsignedByteArray(): ByteArray {
    if (this !in 0..65535) throw IllegalArgumentException("Allowed numbers in range between 0..65535")
    val ba = ByteArray(2)
    ba[1] = ((this shr 8) and 0xff).toByte()
    ba[0] = ((this shr 0) and 0xff).toByte()
    return ba
}
