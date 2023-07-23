package com.ndmsystems.coala.utils

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */ /** A byte buffer is a flexible array which grows when elements are
 * appended. There are also methods to append names to byte buffers
 * and to convert byte buffers to names.
 *
 *
 * **This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.**
 */
class ByteBuffer @JvmOverloads constructor(initialSize: Int = 64) {
    /** An array holding the bytes in this buffer; can be grown.
     */
    var elems: ByteArray

    /** The current number of defined bytes in this buffer.
     */
    var length: Int
    /** Create a new byte buffer with an initial elements array
     * of given size.
     */
    /** Create a new byte buffer.
     */
    init {
        elems = ByteArray(initialSize)
        length = 0
    }

    private fun copy(size: Int) {
        val newelems = ByteArray(size)
        System.arraycopy(elems, 0, newelems, 0, elems.size)
        elems = newelems
    }

    /** Append byte to this buffer.
     */
    fun appendByte(b: Int) {
        if (length >= elems.size) copy(elems.size * 2)
        elems[length++] = b.toByte()
    }
    /** Append `len' bytes from byte array,
     * starting at given `start' offset.
     */
    /** Append all bytes from given byte array.
     */
    @JvmOverloads
    fun appendBytes(bs: ByteArray, start: Int = 0, len: Int = bs.size) {
        while (length + len > elems.size) copy(elems.size * 2)
        System.arraycopy(bs, start, elems, length, len)
        length += len
    }

    /** Append a character as a two byte number.
     */
    fun appendChar(x: Int) {
        while (length + 1 >= elems.size) copy(elems.size * 2)
        elems[length] = (x shr 8 and 0xFF).toByte()
        elems[length + 1] = (x and 0xFF).toByte()
        length = length + 2
    }

    /** Append an integer as a four byte number.
     */
    fun appendInt(x: Int) {
        while (length + 3 >= elems.size) copy(elems.size * 2)
        elems[length] = (x shr 24 and 0xFF).toByte()
        elems[length + 1] = (x shr 16 and 0xFF).toByte()
        elems[length + 2] = (x shr 8 and 0xFF).toByte()
        elems[length + 3] = (x and 0xFF).toByte()
        length = length + 4
    }

    /** Append a long as an eight byte number.
     */
    fun appendLong(x: Long) {
        val buffer = ByteArrayOutputStream(8)
        val bufout = DataOutputStream(buffer)
        try {
            bufout.writeLong(x)
            appendBytes(buffer.toByteArray(), 0, 8)
        } catch (e: IOException) {
            throw AssertionError("write")
        }
    }

    /** Append a float as a four byte number.
     */
    fun appendFloat(x: Float) {
        val buffer = ByteArrayOutputStream(4)
        val bufout = DataOutputStream(buffer)
        try {
            bufout.writeFloat(x)
            appendBytes(buffer.toByteArray(), 0, 4)
        } catch (e: IOException) {
            throw AssertionError("write")
        }
    }

    /** Append a double as a eight byte number.
     */
    fun appendDouble(x: Double) {
        val buffer = ByteArrayOutputStream(8)
        val bufout = DataOutputStream(buffer)
        try {
            bufout.writeDouble(x)
            appendBytes(buffer.toByteArray(), 0, 8)
        } catch (e: IOException) {
            throw AssertionError("write")
        }
    }

    /** Reset to zero length.
     */
    fun reset() {
        length = 0
    }
}