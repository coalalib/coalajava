package com.ndmsystems.coala.message

/**
 * The enumeration of request codes: GET, POST, PUT and DELETE.
 */
enum class CoAPMessageCode
/**
 * Instantiates a new code with the specified code value.
 *
 * @param value the integer value of the code
 */(val value: Int) {
    GET(1), POST(2), PUT(3), DELETE(4), CoapCodeEmpty(0), CoapCodeCreated(65), CoapCodeDeleted(66), CoapCodeValid(67), CoapCodeChanged(68), CoapCodeContent(
        69
    ),
    CoapCodeContinue(95), CoapCodeBadRequest(128), CoapCodeUnauthorized(129), CoapCodeBadOption(130), CoapCodeForbidden(131), CoapCodeNotFound(132), CoapCodeMethodNotAllowed(
        133
    ),
    CoapCodeNotAcceptable(134), CoapCodeConflict(137), CoapCodePreconditionFailed(140), CoapCodeRequestEntityTooLarge(141), CoapCodeUnsupportedContentFormat(
        143
    ),
    CoapCodeInternalServerError(160), CoapCodeNotImplemented(161), CoapCodeBadGateway(162), CoapCodeServiceUnavailable(163), CoapCodeGatewayTimeout(
        164
    ),
    CoapCodeProxyingNotSupported(165);

    val codeClass: Int
        get() = value and 11100000 shr 5
    private val codeDetail: Int
        get() = value and 11111

    override fun toString(): String {
        return String.format("%d.%02d", codeClass, codeDetail)
    }

    val isRequest: Boolean
        get() = when (this) {
            GET, POST, PUT, DELETE -> true
            else -> false
        }

    companion object {
        /**
         * Converts the specified integer value to a request code.
         *
         * @param value the integer value
         * @return the request code
         * @throws IllegalArgumentException if the integer value does not represent a valid request code.
         */
        @JvmStatic
        fun valueOf(value: Int): CoAPMessageCode {
            return when (value) {
                1 -> GET
                2 -> POST
                3 -> PUT
                4 -> DELETE
                0 -> CoapCodeEmpty
                65 -> CoapCodeCreated
                66 -> CoapCodeDeleted
                67 -> CoapCodeValid
                68 -> CoapCodeChanged
                69 -> CoapCodeContent
                95 -> CoapCodeContinue
                128 -> CoapCodeBadRequest
                129 -> CoapCodeUnauthorized
                130 -> CoapCodeBadOption
                131 -> CoapCodeForbidden
                132 -> CoapCodeNotFound
                133 -> CoapCodeMethodNotAllowed
                134 -> CoapCodeNotAcceptable
                137 -> CoapCodeConflict
                140 -> CoapCodePreconditionFailed
                141 -> CoapCodeRequestEntityTooLarge
                143 -> CoapCodeUnsupportedContentFormat
                160 -> CoapCodeInternalServerError
                161 -> CoapCodeNotImplemented
                162 -> CoapCodeBadGateway
                163 -> CoapCodeServiceUnavailable
                164 -> CoapCodeGatewayTimeout
                165 -> CoapCodeProxyingNotSupported
                else -> throw IllegalArgumentException("Unknown CoAP code $value")
            }
        }
    }
}