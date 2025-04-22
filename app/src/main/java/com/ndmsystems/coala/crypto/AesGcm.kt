package com.ndmsystems.coala.crypto

import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.helpers.logging.LogHelper
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Created by bas on 01.11.16.
 */
class AesGcm(key: ByteArray) {
    private val key: Key
    private var cipher: Cipher? = null

    init {
        this.key = SecretKeySpec(key, "AES")
        initCipher()
    }

    private fun initCipher() = synchronized(key) {
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding")
        } catch (e: Exception) {
            e.printStackTrace()
            LogHelper.e("Fatal error, can't get cipher")
        }
    }

    //TODO: сделать поддержку additionalAuthenticatedData
    @Throws(InvalidAlgorithmParameterException::class, InvalidKeyException::class, BadPaddingException::class, IllegalBlockSizeException::class)
    fun seal(plainText: ByteArray?, nonce: ByteArray?, additionalAuthenticatedData: ByteArray?): ByteArray = synchronized(key) {
        cipher!!.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH * 8, nonce))
        try {
            return cipher!!.doFinal(plainText)
        } catch (ex: java.lang.Exception) {
            initCipher()
            throw ex
        }
    }

    // the input comes from users
    //TODO: сделать поддержку additionalAuthenticatedData
    @Throws(Exception::class)
    fun open(cipherText: ByteArray, nonce: ByteArray, authData: ByteArray?): ByteArray = synchronized(key) {

        // Initialise AES/GCM cipher for decryption
        cipher!!.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH * 8, nonce))
        // Join cipher text and authentication tag to produce cipher input
        val input = ByteArray(cipherText.size)
        System.arraycopy(cipherText, 0, input, 0, cipherText.size)

        // Validate authentication tag
        val result = try {
            cipher!!.doFinal(input)
        } catch (e: java.lang.Exception) {
            throw Exception(
                "${e.javaClass}:Couldn't validate GCM authentication tag when decode, length of the data: " + cipherText.size + ", key: " + Hex.encodeHexString(
                    key.encoded
                ) + ", error: " + e.message, e
            )
        }
        return result
    }

    companion object {

        private const val TAG_LENGTH = 12
    }
}