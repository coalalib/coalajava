package com.ndmsystems.coala.exceptions

import com.ndmsystems.coala.message.CoAPMessageCode

/**
 * Created by Владимир on 21.07.2017.
 */
open class CoAPException : BaseCoalaThrowable {
    val code: CoAPMessageCode
    val payloadErrorCode: Int?

    constructor(code: CoAPMessageCode, message: String?) : super(message!!) {
        this.code = code
        payloadErrorCode = null
    }

    constructor(
        message: String?,
        code: CoAPMessageCode,
        payloadErrorCode: Int
    ) : super(if (!message.isNullOrEmpty()) message else "Handle payload error:$payloadErrorCode") {
        this.code = code
        this.payloadErrorCode = payloadErrorCode
    }

    val payloadError: PayloadError
        get() = PayloadError.getByCode(payloadErrorCode)

    override fun toString(): String {
        return this.javaClass.simpleName + " code: " + code + ", code.value: " + code.value + ", payloadErrorCode: " + payloadErrorCode + ", message: " + message
    }
}