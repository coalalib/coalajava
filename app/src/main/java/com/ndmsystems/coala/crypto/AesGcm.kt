package com.ndmsystems.coala.crypto

import android.os.Build
import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.helpers.logging.LogHelper
import org.spongycastle.crypto.BlockCipher
import org.spongycastle.crypto.CipherParameters
import org.spongycastle.crypto.InvalidCipherTextException
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.modes.GCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter
import org.spongycastle.jcajce.spec.AEADParameterSpec
import org.spongycastle.jce.provider.BouncyCastleProvider
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.Security
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.spec.SecretKeySpec

/**
 * Created by bas on 01.11.16.
 */
class AesGcm(key: ByteArray?) {
    private val key: Key
    var cipher: Cipher? = null

    init {
        this.key = SecretKeySpec(key, "AES")
        initCipher()
    }

    private fun initCipher() {
        try {
            cipher = Cipher.getInstance("AES/GCM/NoPadding", bouncyCastleProvider)
        } catch (e: Exception) {
            e.printStackTrace()
            LogHelper.e("Fatal error, can't get cipher")
        }
    }

    //TODO: сделать поддержку additionalAuthenticatedData
    @Throws(InvalidAlgorithmParameterException::class, InvalidKeyException::class, BadPaddingException::class, IllegalBlockSizeException::class)
    fun seal(plainText: ByteArray?, nonce: ByteArray?, additionalAuthenticatedData: ByteArray?): ByteArray {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            initCipher()
        }
        cipher!!.init(Cipher.ENCRYPT_MODE, key, AEADParameterSpec(nonce, TAG_LENGTH * 8))
        return cipher!!.doFinal(plainText)
    }

    // the input comes from users
    //TODO: сделать поддержку additionalAuthenticatedData
    @Throws(Exception::class)
    fun open(cipherText: ByteArray, nonce: ByteArray, authData: ByteArray?): ByteArray {

        // Initialise AES/GCM cipher for decryption
        val cipher = createAESGCMCipher(key, false, nonce, authData)
        // Join cipher text and authentication tag to produce cipher input
        val input = ByteArray(cipherText.size)
        System.arraycopy(cipherText, 0, input, 0, cipherText.size)
        val outputLength = cipher.getOutputSize(input.size)
        val output = ByteArray(outputLength)

        // Decrypt
        var outputOffset = cipher.processBytes(input, 0, input.size, output, 0)

        // Validate authentication tag
        outputOffset += try {
            cipher.doFinal(output, outputOffset)
        } catch (e: InvalidCipherTextException) {
            throw Exception(
                "Couldn't validate GCM authentication tag when decode, length of the data: " + cipherText.size + ", key: " + Hex.encodeHexString(
                    key.encoded
                ) + ", error: " + e.message, e
            )
        }
        return output
    }

    companion object {
        private val bouncyCastleProvider: BouncyCastleProvider = BouncyCastleProvider()

        init {
            Security.insertProviderAt(bouncyCastleProvider, 1)
        }

        private const val TAG_LENGTH = 12
        private fun createAESCipher(
            secretKey: Key,
            forEncryption: Boolean
        ): AESEngine {
            val cipher = AESEngine()
            val cipherParams: CipherParameters = KeyParameter(secretKey.encoded)
            cipher.init(forEncryption, cipherParams)
            return cipher
        }

        private fun createAESGCMCipher(
            secretKey: Key,
            forEncryption: Boolean,
            iv: ByteArray,
            authData: ByteArray?
        ): GCMBlockCipher {

            // Initialise AES cipher
            val cipher: BlockCipher = createAESCipher(secretKey, forEncryption)

            // Create GCM cipher with AES
            val gcm = GCMBlockCipher(cipher)
            val aeadParams = AEADParameters(
                KeyParameter(secretKey.encoded),
                TAG_LENGTH * 8,
                iv,
                authData
            )
            gcm.init(forEncryption, aeadParams)
            return gcm
        }
    }
}