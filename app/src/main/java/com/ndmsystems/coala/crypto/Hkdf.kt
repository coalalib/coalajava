/**
 * Copyright (C) 2013-2016 decrypt Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package com.ndmsystems.coala.crypto

import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.helpers.logging.LogHelper
import java.io.ByteArrayOutputStream
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

class Hkdf(sharedSecret: ByteArray, salt: ByteArray?, info: ByteArray?) {
    @JvmField
    val peerKey = ByteArray(KEY_LEN)
    @JvmField
    val myKey = ByteArray(KEY_LEN)
    @JvmField
    val peerIV = ByteArray(4)
    @JvmField
    val myIV = ByteArray(4)

    init {
        val okm = deriveSecrets(sharedSecret, salt, info)
        LogHelper.v("OKM: " + Hex.encodeHexString(okm))
        System.arraycopy(okm, 0, peerKey, 0, KEY_LEN)
        System.arraycopy(okm, KEY_LEN, myKey, 0, KEY_LEN)
        System.arraycopy(okm, 2 * KEY_LEN, peerIV, 0, 4)
        System.arraycopy(okm, 2 * KEY_LEN + 4, myIV, 0, 4)
    }

    override fun toString(): String {
        return Hex.encodeHexString(peerKey) + Hex.encodeHexString(myKey) + Hex.encodeHexString(peerIV) + Hex.encodeHexString(myIV)
    }

    private fun deriveSecrets(inputKeyMaterial: ByteArray, salt: ByteArray?, info: ByteArray?): ByteArray {
        var mutableSalt: ByteArray? = salt
        if (salt == null || salt.isEmpty()) mutableSalt = ByteArray(HASH_OUTPUT_SIZE)
        val prk = extract(mutableSalt!!, inputKeyMaterial)
        return expand(prk, info, OUTPUT_LENGTH)
    }

    private fun extract(salt: ByteArray, inputKeyMaterial: ByteArray): ByteArray {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(salt, "HmacSHA256"))
            mac.doFinal(inputKeyMaterial)
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        } catch (e: InvalidKeyException) {
            throw AssertionError(e)
        }
    }

    private fun expand(prk: ByteArray, info: ByteArray?, outputSize: Int): ByteArray {
        return try {
            val iterations = ceil(outputSize.toDouble() / HASH_OUTPUT_SIZE.toDouble()).toInt()
            var mixin: ByteArray? = ByteArray(0)
            val results = ByteArrayOutputStream()
            var remainingBytes = outputSize
            for (i in 1 until iterations + 1) {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(prk, "HmacSHA256"))
                mac.update(mixin)
                if (info != null) {
                    mac.update(info)
                }
                mac.update(i.toByte())
                val stepResult = mac.doFinal()
                val stepSize = Math.min(remainingBytes, stepResult.size)
                results.write(stepResult, 0, stepSize)
                mixin = stepResult
                remainingBytes -= stepSize
            }
            results.toByteArray()
        } catch (e: NoSuchAlgorithmException) {
            throw AssertionError(e)
        } catch (e: InvalidKeyException) {
            throw AssertionError(e)
        }
    }

    companion object {
        private const val HASH_OUTPUT_SIZE = 32
        private const val OUTPUT_LENGTH = 40
        private const val KEY_LEN = 16
    }
}