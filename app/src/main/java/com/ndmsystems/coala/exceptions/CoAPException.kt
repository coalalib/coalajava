package com.ndmsystems.coala.exceptions

import com.ndmsystems.coala.message.CoAPMessageCode

/**
 * Created by Владимир on 21.07.2017.
 */
open class CoAPException : BaseCoalaThrowable {
    val code: CoAPMessageCode
    val payloadErrorCode: Int?
    private val srcMessage: String

    constructor(code: CoAPMessageCode, message: String?) : super("Code: $code, $message") {
        this.code = code
        payloadErrorCode = null
        srcMessage = message ?: ""
    }

    constructor(
        message: String?,
        code: CoAPMessageCode,
        payloadErrorCode: Int
    ) : super(if (!message.isNullOrEmpty()) "Code: $code, $message, $payloadErrorCode" else "Handle payload error: code $code, $payloadErrorCode") {
        this.code = code
        this.payloadErrorCode = payloadErrorCode
        srcMessage = message ?: ""
    }

    val payloadError: PayloadError
        get() = PayloadError.getByCode(payloadErrorCode)

    override fun toString(): String {
        return this.javaClass.simpleName + " code: " + code + ", code.value: " + code.value + ", payloadErrorCode: " + payloadErrorCode + ", message: " + message
    }
}