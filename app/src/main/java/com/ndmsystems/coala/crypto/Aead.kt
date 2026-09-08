package com.ndmsystems.coala.crypto

import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogKeys
import java.nio.ByteBuffer

class Aead(peerKey: ByteArray, myKey: ByteArray, peerIV: ByteArray, myIV: ByteArray) {
    private val peerKey: ByteArray
    private val myKey: ByteArray
    private val peerIV: ByteArray
    private val myIV: ByteArray
    private val encryptor: AesGcm
    private val decryptor: AesGcm

    init {
        // The names matter: LogSanitizer blanks these four by key before anything is uploaded.
        // Logcat still shows them - ILogger.log's default folds the fields back into the text -
        // so local debugging keeps the material and the wire never sees it.
        LogHelper.v(
            "Aead keys",
            mapOf(
                "peer_key" to Hex.encodeHexString(peerKey),
                "peer_key_length" to peerKey.size,
                "my_key" to Hex.encodeHexString(myKey),
                "my_key_length" to myKey.size,
                "peer_iv" to Hex.encodeHexString(peerIV),
                "peer_iv_length" to peerIV.size,
                "my_iv" to Hex.encodeHexString(myIV),
                "my_iv_length" to myIV.size
            )
        )
        this.peerKey = peerKey
        this.myKey = myKey
        this.peerIV = peerIV
        this.myIV = myIV
        encryptor = AesGcm(this.myKey)
        decryptor = AesGcm(this.peerKey)
    }

    fun decrypt(cipherText: ByteArray, counter: Int, associatedData: ByteArray?): ByteArray? {
        return try {
            decryptor.open(cipherText, makeNonce(peerIV, counter), associatedData)
        } catch (e: Exception) {
            e.printStackTrace()
            LogHelper.e(
                "Error then decrypt",
                mapOf(
                    LogKeys.ERROR to e.message,
                    "nonce" to Hex.encodeHexString(makeNonce(peerIV, counter))
                )
            )
            null
        }
    }

    override fun toString(): String {
        return Hex.encodeHexString(peerKey) + Hex.encodeHexString(myKey) + Hex.encodeHexString(peerIV) + Hex.encodeHexString(myIV)
    }

    fun encrypt(plainText: ByteArray, counter: Int, associatedData: ByteArray?): ByteArray? {
        return try {
            encryptor.seal(plainText, makeNonce(myIV, counter), associatedData)
        } catch (e: Exception) {
            e.printStackTrace()
            LogHelper.e(
                "Error then encrypt",
                mapOf(LogKeys.ERROR_TYPE to e.javaClass.name, LogKeys.ERROR to e.message)
            )
            null
        }
    }

    private fun makeNonce(iv: ByteArray, counter: Int): ByteArray {
        val nonce = ByteBuffer.allocate(12)
        nonce.put(iv).put((counter and 0xFF).toByte()).put((counter shr 8 and 0xFF).toByte())
        return nonce.array()
    }
}