package com.ndmsystems.coala.helpers

import com.ndmsystems.coala.crypto.Aead
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.w
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload

object EncryptionHelper {
    @JvmStatic
    fun encrypt(message: CoAPMessage, aead: Aead) {
        if (message.payload != null) message.payload =
            CoAPMessagePayload(aead.encrypt(message.payload!!.content, message.id, null) ?: ByteArray(0))
        encryptOptions(message, aead)
    }

    private fun encryptOptions(message: CoAPMessage, aead: Aead) {
        if (message.hasOption(CoAPMessageOptionCode.OptionURIQuery)
            || message.hasOption(CoAPMessageOptionCode.OptionURIPath)
        ) {
            message.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionCoapsURI, aead.encrypt(message.getURI().toByteArray(), message.id, null) ?: ByteArray(0)))
            message.removeOption(CoAPMessageOptionCode.OptionURIQuery)
            message.removeOption(CoAPMessageOptionCode.OptionURIPath)
        }
    }

    @JvmStatic
    fun decrypt(message: CoAPMessage, aead: Aead): Boolean {
        if (message.payload != null) {
            val newPayload = aead.decrypt(message.payload!!.content, message.id, null)
            if (newPayload == null) {
                e("Can't decrypt message with id: " + message.id + ", token: " + message.hexToken)
                message.payload = null
                return false
            }
            message.payload = CoAPMessagePayload(newPayload)
        }
        decryptOptions(message, aead)
        return true
    }

    private fun decryptOptions(message: CoAPMessage, aead: Aead) {
        if (message.hasOption(CoAPMessageOptionCode.OptionCoapsURI)) {
            val uriBytes = aead.decrypt(message.getOption(CoAPMessageOptionCode.OptionCoapsURI)!!.toBytes(), message.id, null)
            if (uriBytes != null) {
                message.setURI(String(uriBytes))
                message.removeOption(CoAPMessageOptionCode.OptionCoapsURI)
            } else w("OptionCoapsURI empty after decrypt")
        }
    }
}