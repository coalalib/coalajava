package com.ndmsystems.coala.crypto

import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.helpers.logging.LogHelper
import java.nio.ByteBuffer

class Aead(peerKey: ByteArray, myKey: ByteArray, peerIV: ByteArray, myIV: ByteArray) {
    private val peerKey: ByteArray
    private val myKey: ByteArray
    private val peerIV: ByteArray
    private val myIV: ByteArray
    private val encryptor: AesGcm
    private val decryptor: AesGcm

    init {
        LogHelper.v("Aead peerKey=" + Hex.encodeHexString(peerKey) + ", length: " + peerKey.size)
        LogHelper.v("Aead myKey=" + Hex.encodeHexString(myKey) + ", length: " + myKey.size)
        LogHelper.v("Aead peerIV=" + Hex.encodeHexString(peerIV) + ", length: " + peerIV.size)
        LogHelper.v("Aead myIV=" + Hex.encodeHexString(myIV) + ", length: " + myIV.size)
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
            LogHelper.e("Error then decrypt: " + e.message + ", nonce: " + Hex.encodeHexString(makeNonce(peerIV, counter)))
            null
        }
    }

    override fun toString(): String {
        return Hex.encodeHexString(peerKey) + Hex.encodeHexString(myKey) + Hex.encodeHexString(peerIV) + Hex.encodeHexString(myIV)
    }

    fun encrypt(plainText: ByteArray?, counter: Int, associatedData: ByteArray?): ByteArray? {
        return try {
            encryptor.seal(plainText, makeNonce(myIV, counter), associatedData)
        } catch (e: Exception) {
            e.printStackTrace()
            LogHelper.e("Error then encrypt: " + e.message)
            null
        }
    }

    private fun makeNonce(iv: ByteArray, counter: Int): ByteArray {
        val nonce = ByteBuffer.allocate(12)
        nonce.put(iv).put((counter and 0xFF).toByte()).put((counter shr 8 and 0xFF).toByte())
        return nonce.array()
    }
}