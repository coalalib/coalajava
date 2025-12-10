package com.ndmsystems.coala.layers.response

import com.ndmsystems.coala.exceptions.CoAPException
import com.ndmsystems.coala.exceptions.WrongAuthDataException
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import org.json.JSONException
import org.json.JSONObject

class ResponseErrorFactory {
    fun proceed(message: CoAPMessage, request: CoAPMessage? = null): CoAPException? {
        if (message.type == CoAPMessageType.RST) {
            return CoAPException(message.code, if (message.payload == null || message.payload.toString() == "null") "Request has been reset, REF!" else message.payload.toString())
        } else if (message.code.codeClass != 2) {
            if (message.payload == null) return proceedByResponseCode(message)
            if (message.payload.toString() == "Wrong login or password") {
                return WrongAuthDataException(message.code, CoAPMessageCode.CoapCodeUnauthorized.name)
            } else if (message.payload.toString().contains("code")) {
                return proceedByResponsePayloadErrorCode(message, request)
            }
            return proceedByResponseCode(message)
        }
        return null
    }

    private fun proceedByResponseCode(message: CoAPMessage): CoAPException {
        return when (message.code) {
            CoAPMessageCode.CoapCodeUnauthorized -> WrongAuthDataException(message.code, CoAPMessageCode.CoapCodeUnauthorized.name)
            else -> CoAPException(message.code, if (message.payload == null || message.payload.toString() == "null") "Request has been reset, REF, code ${message.code}!" else message.payload.toString())
        }
    }

    private fun proceedByResponsePayloadErrorCode(message: CoAPMessage, request: CoAPMessage? = null): CoAPException? {
        var coAPException: CoAPException? = null
        try {
            val errorObject = JSONObject(message.payload.toString())
            val errorMessage = if (errorObject.has("message")) errorObject.getString("message") else ""
            val payloadErrorCode = if (errorObject.has("code")) errorObject.getInt("code") else 0
            coAPException = CoAPException(errorMessage, message.code, payloadErrorCode, "req payload: ${request?.payload.toString()}, req path: ${request?.getURIPathString()}")
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return coAPException
    }
}