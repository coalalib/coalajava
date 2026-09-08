package com.ndmsystems.coala.layers.security.session

import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogKeys
import com.ndmsystems.coala.crypto.Aead
import com.ndmsystems.coala.crypto.CurveRepository
import com.ndmsystems.coala.crypto.Curve25519
import com.ndmsystems.coala.crypto.Hkdf
import com.ndmsystems.coala.helpers.Hex.encodeHexString
import com.ndmsystems.coala.helpers.RBGHelper.rbg
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * @param curveRepository where the local key pair comes from. Passed in rather than fetched from a
 * global: two Coala instances in one process used to share whichever one was built last, which made
 * the handshake impossible to test in isolation and would have had two peers signing with the same
 * key material.
 */
class SecuredSession(incoming: Boolean, private val curveRepository: CurveRepository) {
    private var curve: Curve25519? = null
        get() {
            if (field == null) {
                field = curveRepository.curve
            }
            return field
        }
    var aead: Aead? = null
        private set
    var peerPublicKey: ByteArray? = null
        private set
    var peerProxySecurityId: Long? = null
    val publicKey: ByteArray
        get() {
            val publicKey = curve!!.publicKey
            LogHelper.v("getPublicKey", mapOf("public_key" to encodeHexString(publicKey)))
            return publicKey
        }

    // Generating Shared Secret based on: MyPrivateKey + PeerPublicKey
    private val signature: ByteArray
        get() {
            // Generating Shared Secret based on: MyPrivateKey + PeerPublicKey
            val sharedSecret = curve!!.generateSharedSecret(peerPublicKey)
            val md: MessageDigest = try {
                MessageDigest.getInstance("SHA-256")
            } catch (e: NoSuchAlgorithmException) {
                LogHelper.e("SecuredSession getSignature NoSuchAlgorithmException", mapOf(LogKeys.ERROR to e.message))
                return ByteArray(0)
            }
            md.update(sharedSecret)
            return md.digest()
        }

    fun start(peerPublicKey: ByteArray) {
        LogHelper.d("SecuredSession start start")
        this.peerPublicKey = peerPublicKey

        // Generating Shared Secret based on: MyPrivateKey + PeerPublicKey
        val sharedSecret = curve!!.generateSharedSecret(this.peerPublicKey)
        val salt: ByteArray? = null
        val info: ByteArray? = null // Should be some public data

        // OK! Session is started! We can communicate now with AES Ephemeral Key!
        setAead(parseHKDF(Hkdf(sharedSecret, salt, info)))
        LogHelper.d("SecuredSession start end")
    }

    private fun parseHKDF(hkdf: Hkdf): Aead {
        return Aead(hkdf.peerKey, hkdf.myKey, hkdf.peerIV, hkdf.myIV)
    }

    fun startPeer(peerPublicKey: ByteArray) {
        LogHelper.d("SecuredSession start start")
        this.peerPublicKey = peerPublicKey

        // Generating Shared Secret based on: MyPrivateKey + PeerPublicKey
        val sharedSecret = curve!!.generateSharedSecret(this.peerPublicKey)
        val salt: ByteArray? = null
        val info: ByteArray? = null // Should be some public data

        // OK! Session is started! We can communicate now with AES Ephemeral Key!
        setAead(parseHKDFPeer(Hkdf(sharedSecret, salt, info)))
        LogHelper.d("SecuredSession start end")
    }

    private fun parseHKDFPeer(hkdf: Hkdf): Aead {
        return Aead(hkdf.myKey, hkdf.peerKey, hkdf.myIV, hkdf.peerIV)
    }

    // ASSIGN KEYS IN REVERS ORDER!!!!
    fun PeerVerify(peerSignature: ByteArray) {
        val signature = signature

        // If the Peer is not a Man-In-The-Middle then Peer's Shared Secret is the Same!
        // Hash our Shared Secret to Compare with Peer's Signature!
        if (signature != peerSignature) {
            LogHelper.e("signature and peerSignature are not Equal")
        }

        // Generating Shared Secret based on: MyPrivateKey + PeerPublicKey
        val sharedSecret = curve!!.generateSharedSecret(peerPublicKey)
        val nonce = rbg(32) // Just random data
        val info = ByteArray(0) // Should be some public data

        //byte[] keys  = new Hkdf().deriveSecrets(sharedSecret, nonce, info);

        // ASSIGN KEYS IN REVERS ORDER!!!!
        //aead = parseHKDFKeysForPeer(keys);
    }

    val isReady: Boolean
        get() = aead != null

    private fun setAead(aead: Aead) {
        this.aead = aead
    }
}