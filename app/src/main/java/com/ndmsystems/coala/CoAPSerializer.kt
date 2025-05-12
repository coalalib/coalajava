package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.w
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.util.Collections

/**
 * CoAP message starts with a fixed-size 4-byte header.
 *
 *
 * This is followed by a variable-length Token value, which can be between 0 and 8 bytes long.
 *
 *
 * Following the Token value comes a sequence of zero or more CoAP
 * Options in Type-Length-Value (TLV) format, optionally followed by a
 * payload that takes up the rest of the datagram.
 *
 *
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |Ver| T |  TKL  |      Code     |          Message ID           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   Token (if any, TKL bytes) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |   Options (if any) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |1 1 1 1 1 1 1 1|    Payload (if any) ...
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 *
 * The fields in the header are defined as follows:
 *
 *
 * Version (Ver):  2-bit unsigned integer.  Indicates the CoAP version
 * number.  Implementations of this specification MUST set this field
 * to 1 (01 binary).  Other values are reserved for future versions.
 * Messages with unknown version numbers MUST be silently ignored.
 *
 *
 * Type (T):  2-bit unsigned integer.  Indicates if this message is of
 * type Confirmable (0), Non-confirmable (1), Acknowledgement (2), or
 * Reset (3).
 *
 *
 * Token Length (TKL):  4-bit unsigned integer.  Indicates the length of
 * the variable-length Token field (0-8 bytes).  Lengths 9-15 are
 * reserved, MUST NOT be sent, and MUST be processed as a message
 * format error.
 *
 *
 * Code:  8-bit unsigned integer, split into a 3-bit class (most
 * significant bits) and a 5-bit detail (least significant bits),
 * documented as "c.dd" where "c" is a digit from 0 to 7 for the
 * 3-bit subfield and "dd" are two digits from 00 to 31 for the 5-bit
 * subfield.
 */
object CoAPSerializer {
    private const val COAP_PAYLOAD_MARKER = 0xff
    private const val COAP_HEADER_SIZE = 4
    private const val COAP_PROTOCOL_VERSION = 1
    private const val MAX_OPTION_DELTA = 65804
    private const val MAX_OPTION_LENGTH = 65804
    private const val MAX_TOKEN_LENGTH = 8

    /**
     * @param data
     * @return
     */
    @JvmStatic
    @Throws(DeserializeException::class)
    fun fromBytes(data: ByteArray, addressFrom: InetSocketAddress? = null): CoAPMessage? {
        if (data.size < COAP_HEADER_SIZE) {
            raiseDeserializeException(null, "Encoded CoAP messages MUST have min. 4 bytes. This has " + data.size)
            return null
        }
        val version = data[0].toInt() ushr 6
        val type = data[0].toInt() ushr 4 and 3
        val tokenLength = data[0].toInt() and 15
        val code = data[1].toInt() and 0xFF
        val messageId = data[2].toInt() and 0xFF shl 8 or (data[3].toInt() and 0xFF)

        // Check whether the protocol version is supported (=1)
        if (version != COAP_PROTOCOL_VERSION) {
            raiseDeserializeException(messageId, "Invalid CoAP version $version. Should be: $COAP_PROTOCOL_VERSION + from address $addressFrom")
            return null
        }

        // Check whether TKL indicates a not allowed token length
        if (tokenLength > MAX_TOKEN_LENGTH) {
            raiseDeserializeException(messageId, "TKL value ($tokenLength) is larger than 8!")
            return null
        }

        // Check whether there are enough unread bytes left to read the token
        if (data.size - COAP_HEADER_SIZE < tokenLength) {
            raiseDeserializeException(messageId, "TKL value is " + tokenLength + " but only " + (data.size - COAP_HEADER_SIZE) + " bytes left!")
            return null
        }

        /* Validate */
        val messageType: CoAPMessageType
        messageType = try {
            CoAPMessageType.valueOf(type)
        } catch (e: IllegalArgumentException) {
            raiseDeserializeException(messageId, e.message)
            return null
        }
        val messageCode: CoAPMessageCode
        messageCode = try {
            CoAPMessageCode.valueOf(code)
        } catch (e: IllegalArgumentException) {
            raiseDeserializeException(messageId, e.message)
            return null
        }
        val message = CoAPMessage(messageType, messageCode, messageId)

        // Read the token
        if (tokenLength > 0) {
            val token = ByteArray(tokenLength)
            System.arraycopy(data, COAP_HEADER_SIZE, token, 0, tokenLength)
            message.token = token
        }

        // This is how many bytes we should pass before further data reading
        val offset = COAP_HEADER_SIZE + tokenLength
        if (data.size - offset > 0) {
            val `is` = ByteArrayInputStream(data, offset, data.size)

            /* Read Options */decodeOptions(message, `is`)

            /* Read Payload */if (`is`.available() > 0) {
                val payload = ByteArray(`is`.available())
                try {
                    `is`.read(payload)
                    message.payload = CoAPMessagePayload(payload)
                } catch (e: IOException) {
                    raiseDeserializeException(messageId, e.message)
                    return null
                }
            }
        }
        return message
    }

    @Throws(DeserializeException::class)
    private fun raiseDeserializeException(messageId: Int?, exceptionString: String?) {
        e(exceptionString!!)
        throw DeserializeException(exceptionString)
    }

    /**
     * 0   1   2   3   4   5   6   7
     * +---------------+---------------+
     * |               |               |
     * |  Option Delta | Option Length |   1 byte
     * |               |               |
     * +---------------+---------------+
     * \                               \
     * /         Option Delta          /   0-2 bytes
     * \          (extended)           \
     * +-------------------------------+
     * \                               \
     * /         Option Length         /   0-2 bytes
     * \          (extended)           \
     * +-------------------------------+
     * \                               \
     * /                               /
     * \                               \
     * /         Option Value          /   0 or more bytes
     * \                               \
     * /                               /
     * \                               \
     * +-------------------------------+
     */
    private fun decodeOptions(message: CoAPMessage, `is`: ByteArrayInputStream) {
        //Decode the options
        var previousOptionNumber = 0
        var firstByte = `is`.read() and 0xFF
        while (firstByte != 0xFF && `is`.available() >= 0) {
            var optionDelta = firstByte and 0xF0 ushr 4
            var optionLength = firstByte and 0x0F
            if (optionDelta == 13) {
                optionDelta += `is`.read() and 0xFF
            } else if (optionDelta == 14) {
                optionDelta = 269 + (`is`.read() and 0xFF shl 8) + (`is`.read() and 0xFF)
            }
            if (optionLength == 13) {
                optionLength += `is`.read() and 0xFF
            } else if (optionLength == 14) {
                optionLength = 269 + (`is`.read() and 0xFF shl 8) + (`is`.read() and 0xFF)
            }
            val actualOptionNumber = previousOptionNumber + optionDelta
            try {
                val optionValue = ByteArray(optionLength)
                `is`.read(optionValue)
                val optionCode = CoAPMessageOptionCode.valueOf(actualOptionNumber)
                message.addOption(CoAPMessageOption(optionCode, optionValue))
            } catch (e: IOException) {
                e(e.message!!)
                continue
            } catch (e: IllegalArgumentException) {
                e(e.message!!)
                continue
            }
            previousOptionNumber = actualOptionNumber
            firstByte = if (`is`.available() > 0) {
                `is`.read() and 0xFF
            } else {
                // this is necessary if there is no payload and the last option is empty (e.g. UintOption with value 0)
                0xFF
            }
        }
    }

    fun toBytes(message: CoAPMessage): ByteArray? {
        // start encoding
        val buffer = ByteArrayOutputStream()

        // encode HEADER and TOKEN
        try {
            encodeHeader(buffer, message)
        } catch (e: IOException) {
            e(e.message!!)
            return null
        }

        // encode OPTIONS (if any)
        try {
            encodeOptions(buffer, message)
        } catch (e: Exception) {
            e(e.message!!)
            return null
        }

        // encode payload (if any)
        if (message.payload != null && message.payload!!.content != null) {
            val rawPayload = message.payload!!.content
            if (rawPayload.isNotEmpty()) {
                // add END-OF-OPTIONS marker only if there is payload
                buffer.write(COAP_PAYLOAD_MARKER)
                // add payload
                buffer.write(rawPayload, 0, rawPayload.size)
            }
        }
        return buffer.toByteArray()
    }

    @Throws(IOException::class)
    private fun encodeHeader(buffer: ByteArrayOutputStream, message: CoAPMessage) {
        val token = message.token
        val encodedHeader = (COAP_PROTOCOL_VERSION and 0x03 shl 30
                or (message.type.value and 0x03 shl 28)
                or ((token?.size ?: 0) and 0x0F shl 24)
                or (message.code.value and 0xFF shl 16)
                or (message.id and 0xFFFF))
        buffer.write(encodedHeader and -0x1000000 shr 24)
        buffer.write(encodedHeader and 0x00FF0000 shr 16)
        buffer.write(encodedHeader and 0x0000FF00 shr 8)
        buffer.write(encodedHeader and 0x000000FF)

        // Write token
        if (token != null && token.isNotEmpty()) {
            buffer.write(token, 0, token.size)
        }
    }

    @Throws(Exception::class)
    private fun encodeOptions(buffer: ByteArrayOutputStream, message: CoAPMessage) {
        val options = message.getOptions()
        if (options == null || options.isEmpty()) {
            return
        }

        // sort options by Option Number first
        Collections.sort(options) { obj: CoAPMessageOption, other: CoAPMessageOption? ->
            obj.compareTo(
                other!!
            )
        }

        //Encode options one after the other and append buf option to the buf
        var previousOptionNumber = 0
        for (option in options) {
            // encode
            encodeOption(buffer, option, previousOptionNumber)

            // remember previous number
            previousOptionNumber = option.code.value
        }
    }

    @Throws(Exception::class)
    private fun encodeOption(buffer: ByteArrayOutputStream, option: CoAPMessageOption, previousNumber: Int) {
        val optionNumber = option.code.value

        //The previous option number must be smaller or equal to the actual one
        if (previousNumber > optionNumber) {
            throw Exception("Previous option $previousNumber must not be larger then current option no $optionNumber")
        }
        val rawOptionValue = option.toBytes()
        if (rawOptionValue.isEmpty()) w("Option with null length: $optionNumber")
        val optionDelta = optionNumber - previousNumber
        val optionLength = Math.min(rawOptionValue.size, option.maxSizeInBytes)
        if (optionLength > MAX_OPTION_LENGTH) {
            throw Exception("Option no. $optionNumber exceeds maximum option length: $optionLength vs $MAX_OPTION_LENGTH")
        }
        if (optionDelta > MAX_OPTION_DELTA) {
            throw Exception("Option no. $optionNumber exceeds maximum option delta: $optionDelta vs $MAX_OPTION_DELTA")
        }
        if (optionNumber == CoAPMessageOptionCode.OptionContentFormat.value) {
            d("encodeOption, length: $optionLength, delta: $optionDelta")
        }
        if (optionDelta < 13) {
            //option delta < 13
            if (optionLength < 13) {
                buffer.write(optionDelta and 0xFF shl 4 or (optionLength and 0xFF))
            } else if (optionLength < 269) {
                buffer.write(optionDelta shl 4 and 0xFF or (13 and 0xFF))
                buffer.write(optionLength - 13 and 0xFF)
            } else {
                buffer.write(optionDelta shl 4 and 0xFF or (14 and 0xFF))
                buffer.write(optionLength - 269 and 0xFF00 ushr 8)
                buffer.write(optionLength - 269 and 0xFF)
            }
        } else if (optionDelta < 269) {
            //13 <= option delta < 269
            if (optionLength < 13) {
                buffer.write(13 and 0xFF shl 4 or (optionLength and 0xFF))
                buffer.write(optionDelta - 13 and 0xFF)
            } else if (optionLength < 269) {
                buffer.write(13 and 0xFF shl 4 or (13 and 0xFF))
                buffer.write(optionDelta - 13 and 0xFF)
                buffer.write(optionLength - 13 and 0xFF)
            } else {
                buffer.write(13 and 0xFF shl 4 or (14 and 0xFF))
                buffer.write(optionDelta - 13 and 0xFF)
                buffer.write(optionLength - 269 and 0xFF00 ushr 8)
                buffer.write(optionLength - 269 and 0xFF)
            }
        } else {
            //269 <= option delta < 65805
            if (optionLength < 13) {
                buffer.write(14 and 0xFF shl 4 or (optionLength and 0xFF))
                buffer.write(optionDelta - 269 and 0xFF00 ushr 8)
                buffer.write(optionDelta - 269 and 0xFF)
            } else if (optionLength < 269) {
                buffer.write(14 and 0xFF shl 4 or (13 and 0xFF))
                buffer.write(optionDelta - 269 and 0xFF00 ushr 8)
                buffer.write(optionDelta - 269 and 0xFF)
                buffer.write(optionLength - 13 and 0xFF)
            } else {
                buffer.write(14 and 0xFF shl 4 or (14 and 0xFF))
                buffer.write(optionDelta - 269 and 0xFF00 ushr 8)
                buffer.write(optionDelta - 269 and 0xFF)
                buffer.write(optionLength - 269 and 0xFF00 ushr 8)
                buffer.write(optionLength - 269 and 0xFF)
            }
        }

        // Write option value
        buffer.write(rawOptionValue, 0, optionLength)
    }

    class DeserializeException(message: String?) : Exception(message)
}