package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import org.junit.Assert.assertArrayEquals
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/*
 */
object CoapSerializerSpek : Spek({

    val loremText = "The quick brown fox jumps over the lazy dog."

    fun roundTripPayload(payload: ByteArray): CoAPMessage {
        val source = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.POST)
        source.payload = CoAPMessagePayload(payload)

        val sourceBytes = CoAPSerializer.toBytes(source)
        assertNotNull(sourceBytes)

        val result = CoAPSerializer.fromBytes(sourceBytes)
        assertNotNull(result)

        return result
    }

    fun assertPayloadRoundTrip(payload: ByteArray) {
        val result = roundTripPayload(payload)

        assertArrayEquals(payload, result.payload!!.content)
    }

    fun assertStringPayloadRoundTrip(payload: String) {
        val result = roundTripPayload(payload.toByteArray(StandardCharsets.UTF_8))

        assertEquals(payload, result.payload.toString())
        assertArrayEquals(payload.toByteArray(StandardCharsets.UTF_8), result.payload!!.content)
    }

    /**
     * The smallest CoAP message is 4 bytes in length
     * 1 byte - [0..1] - protocol version
     *          [2..3] - message type
     *          [4..7] - token length
     * 2 byte - message request/response code
     * 3 & 4 bytes - message id
     */

    describe("Testing messages serialization and deserialization") {

        //done
        describe("a minimum message size") {

            it("deserialization failed when CoAP message is malformed") {
                val exception = assertFailsWith(CoAPSerializer.DeserializeException::class) {
                    CoAPSerializer.fromBytes(byteArrayOf(0))
                }
                assertEquals("Encoded CoAP messages MUST have min. 4 bytes. This has 1", exception.message)
            }

        }

        //done
        describe("testing CoAP version") {

            it("deserialization successful with the correct protocol version") {
                val binaryCoAPSimpleMessage = byteArrayOf(0b01000000.toByte(), 1, 0, 0)
                val coapMessage = CoAPSerializer.fromBytes(binaryCoAPSimpleMessage)
                assertNotNull(coapMessage)
            }

            it("deserialization failed with the unknown protocol version") {
                val binaryCoAPSimpleMessage = byteArrayOf(0b11000000.toByte(), 1, 0, 0)
                assertFailsWith(CoAPSerializer.DeserializeException::class) {
                    CoAPSerializer.fromBytes(binaryCoAPSimpleMessage)
                }
            }
        }

        //done
        describe("testing CoAP message type") {

            val messageTypesCases = listOf(
                    CoAPMessageType.RST,
                    CoAPMessageType.NON,
                    CoAPMessageType.ACK,
                    CoAPMessageType.CON
            )

            messageTypesCases.forEach { expectedValue ->
                lateinit var message: CoAPMessage
                lateinit var result: ByteArray
                before {
                    message = CoAPMessage(expectedValue, CoAPMessageCode.GET)
                }
                context("testing different message types") {

                    it("serialization successful with message type $expectedValue") {
                        result = CoAPSerializer.toBytes(message)!!
                        assertNotNull(result)
                    }

                    it("deserialization successful with message type $expectedValue") {
                        val result = CoAPSerializer.fromBytes(result)
                        assertEquals(expectedValue, result!!.type)
                    }
                }
            }
        }
        //done
        describe("testing CoAP message code") {

            val messageCodeCases = listOf(
                    CoAPMessageCode.GET,
                    CoAPMessageCode.POST,
                    CoAPMessageCode.PUT,
                    CoAPMessageCode.DELETE,
                    CoAPMessageCode.CoapCodeEmpty,
                    CoAPMessageCode.CoapCodeCreated,
                    CoAPMessageCode.CoapCodeDeleted,
                    CoAPMessageCode.CoapCodeValid,
                    CoAPMessageCode.CoapCodeChanged,
                    CoAPMessageCode.CoapCodeContent,
                    CoAPMessageCode.CoapCodeContinue,
                    CoAPMessageCode.CoapCodeBadRequest,
                    CoAPMessageCode.CoapCodeUnauthorized,
                    CoAPMessageCode.CoapCodeBadOption,
                    CoAPMessageCode.CoapCodeForbidden,
                    CoAPMessageCode.CoapCodeNotFound,
                    CoAPMessageCode.CoapCodeMethodNotAllowed,
                    CoAPMessageCode.CoapCodeNotAcceptable,
                    CoAPMessageCode.CoapCodeConflict,
                    CoAPMessageCode.CoapCodePreconditionFailed,
                    CoAPMessageCode.CoapCodeRequestEntityTooLarge,
                    CoAPMessageCode.CoapCodeUnsupportedContentFormat,
                    CoAPMessageCode.CoapCodeInternalServerError,
                    CoAPMessageCode.CoapCodeNotImplemented,
                    CoAPMessageCode.CoapCodeBadGateway,
                    CoAPMessageCode.CoapCodeServiceUnavailable,
                    CoAPMessageCode.CoapCodeGatewayTimeout,
                    CoAPMessageCode.CoapCodeProxyingNotSupported
            )
            messageCodeCases.forEach { expectedValue ->
                lateinit var message: CoAPMessage
                lateinit var result: ByteArray
                before {
                    message = CoAPMessage(CoAPMessageType.NON, expectedValue)
                }
                context("testing with message type ${expectedValue.name}") {

                    it("serialization successful with message type $${expectedValue.name}") {
                        result = CoAPSerializer.toBytes(message)!!
                        assertNotNull(result)
                    }
                    it("deserialization successful with message type ${expectedValue.name}") {
                        val result = CoAPSerializer.fromBytes(result)
                        assertEquals(expectedValue, result!!.code)
                    }
                }

            }

            it("fails when unknown message code") {
                val b = byteArrayOf(0b01000000.toByte(), 5, 0, 0)
                val exception = assertFailsWith(CoAPSerializer.DeserializeException::class) {
                    CoAPSerializer.fromBytes(b)
                }
                assertEquals("Unknown CoAP code 5", exception.message)
            }

        }

        //done
        describe("testing token length") {

            it("deserialization failed if the TKL more than MAX_TOKEN_LENGTH") {
                val b = byteArrayOf(121, 1, -69, -116, 125, 27, 117, 127, -76, 2, 0, -41, 1)
                val exception = assertFailsWith(CoAPSerializer.DeserializeException::class) {
                    CoAPSerializer.fromBytes(b)
                }
                assertEquals("TKL value (9) is larger than 8!", exception.message)
            }

            it("deserialization failed if the TKL is defined but token size not match") {
                val b = byteArrayOf(120, 1, -66, 22)
                val exception = assertFailsWith(CoAPSerializer.DeserializeException::class) {
                    CoAPSerializer.fromBytes(b)
                }
                assertEquals("TKL value is 8 but only 0 bytes left!", exception.message)
            }

        }

        //done
        describe("testing message id") {

            val messageIdsCases = listOf(
                    65535, 0,
                    1, 2, 3, 5, 8, 13, 21, 34
            )

            messageIdsCases.forEach { expectedValue ->
                lateinit var message: CoAPMessage
                lateinit var result: ByteArray
                before {
                    message = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET)
                }
                context("testing message with $expectedValue") {

                    it("serialization successful with message id $expectedValue") {
                        message.id = expectedValue
                        result = CoAPSerializer.toBytes(message)!!
                        assertNotNull(result)
                    }

                    it("deserialization successful with message id $expectedValue") {
                        val result = CoAPSerializer.fromBytes(result)
                        assertEquals(expectedValue, result!!.id)
                    }

                }

            }
        }

        //done
        describe("testing token") {

            it("successfully serialize and deserialize token") {
                val source = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.GET)
                source.token = byteArrayOf(125, 27, 117, 127, -76, 2, 0, -41)

                val sourceBytes = CoAPSerializer.toBytes(source)
                assertNotNull(sourceBytes)

                val result = CoAPSerializer.fromBytes(sourceBytes)
                assertNotNull(result)

                assertArrayEquals(byteArrayOf(125, 27, 117, 127, -76, 2, 0, -41), result.token)
            }

            it("deserialization successfully with well-formed token") {
                val binaryCoAPSimpleMessageWithToken = byteArrayOf(
                        120, 1, 0, 0,
                        125, 27, 117, 127, -76, 2, 0, -41)
                val result = CoAPSerializer.fromBytes(binaryCoAPSimpleMessageWithToken)
                assertArrayEquals(byteArrayOf(125, 27, 117, 127, -76, 2, 0, -41), result!!.token)
            }

        }

        //done
        describe("testing payload") {
            it("successfully read payload") {
                var binaryMessage = byteArrayOf(0b01000000.toByte(), 1, 0, 0)
                binaryMessage += 0xff.toByte()
                binaryMessage += loremText.toByteArray(StandardCharsets.UTF_8)

                val result = CoAPSerializer.fromBytes(binaryMessage)

                assertEquals(loremText, result!!.payload.toString())
            }

            it("serializes string payload as UTF-8") {
                val payload = "quotes: \" ' slash: \\ json: {\"key\":\"value\"} newline:\n tab:\t unicode: Привет \uD83D\uDE00"

                assertArrayEquals(
                    payload.toByteArray(StandardCharsets.UTF_8),
                    CoAPMessagePayload(payload).content
                )
            }

            it("setStringPayload stores payload as UTF-8") {
                val payload = "json: {\"message\":\"Привет\"} newline:\n emoji: \uD83D\uDE00"
                val source = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.POST)
                source.setStringPayload(payload)

                assertArrayEquals(payload.toByteArray(StandardCharsets.UTF_8), source.payload!!.content)
            }

            it("successfully serializes and deserializes payload with special characters") {
                val payload = "line1\nline2\r\nquotes: \" ' slash: \\ json: {\"key\":\"value\"} symbols: <>[]{}&?%=+; unicode: Съешь ещё \uD83D\uDE00"

                assertStringPayloadRoundTrip(payload)
            }

            it("successfully serializes and deserializes payload bytes with control values and payload marker") {
                val payload = byteArrayOf(
                    0x00,
                    0x09,
                    0x0A,
                    0x0D,
                    0x22,
                    0x5C,
                    0x7B,
                    0x7D,
                    0x7F,
                    0xFF.toByte(),
                    0xC3.toByte(),
                    0x28
                )

                assertPayloadRoundTrip(payload)
            }

            it("successfully serializes and deserializes every byte value in payload") {
                val payload = ByteArray(256) { it.toByte() }

                assertPayloadRoundTrip(payload)
            }

            it("successfully serializes and deserializes ASCII controls and punctuation") {
                val payload = (0x00..0x7F).joinToString(separator = "") { it.toChar().toString() }

                assertStringPayloadRoundTrip(payload)
            }

            it("successfully serializes and deserializes C1 controls") {
                val payload = (0x80..0x9F).joinToString(separator = "") { it.toChar().toString() }

                assertStringPayloadRoundTrip(payload)
            }

            it("successfully serializes and deserializes unicode whitespace separators") {
                val payload = "\u0009\u000A\u000B\u000C\u000D\u0020\u0085\u00A0\u1680" +
                        "\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A" +
                        "\u2028\u2029\u202F\u205F\u3000"

                assertStringPayloadRoundTrip(payload)
            }

            it("successfully serializes and deserializes unicode format characters") {
                val payload = "\u00AD\u034F\u061C\u180E\u200B\u200C\u200D\u200E\u200F" +
                        "\u202A\u202B\u202C\u202D\u202E\u2060\u2061\u2062\u2063\u2064" +
                        "\u2066\u2067\u2068\u2069\uFEFF"

                assertStringPayloadRoundTrip(payload)
            }

            it("successfully serializes and deserializes combining marks") {
                val payload = "a\u0300\u0301\u0302\u0303\u0304\u0308\u0327\u0338\u034F" +
                        "и\u0306\u0308 e\u20DD"

                assertStringPayloadRoundTrip(payload)
            }

            it("successfully serializes and deserializes paired punctuation and escaping characters") {
                val payload = "\"'`´‘’‚“”„«»‹›()[]{}<>/\\|&?%=+*#@!~^:;,.${'$'}"

                assertStringPayloadRoundTrip(payload)
            }

            it("successfully serializes and deserializes supplementary unicode code points") {
                val payload = listOf(0x1F600, 0x1F9EA, 0x1F680, 0x1F4A9, 0x1D11E, 0x10348)
                    .joinToString(separator = "") { String(Character.toChars(it)) }

                assertStringPayloadRoundTrip(payload)
            }

            it("successfully serializes and deserializes each special character separately") {
                val specialCharacters = listOf(
                    (0x00..0x1F).joinToString(separator = "") { it.toChar().toString() },
                    "\u007F",
                    (0x80..0x9F).joinToString(separator = "") { it.toChar().toString() },
                    "\"'`´‘’‚“”„«»‹›()[]{}<>/\\|&?%=+*#@!~^:;,.${'$'}",
                    "\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000",
                    "\u00AD\u034F\u061C\u180E\u200B\u200C\u200D\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2060\u2061\u2062\u2063\u2064\u2066\u2067\u2068\u2069\uFEFF",
                    "a\u0300\u0301\u0302\u0303\u0304\u0308\u0327\u0338\u034F",
                    listOf(0x1F600, 0x1F9EA, 0x1F680, 0x1F4A9, 0x1D11E, 0x10348)
                        .joinToString(separator = "") { String(Character.toChars(it)) }
                ).joinToString(separator = "")

                var index = 0
                while (index < specialCharacters.length) {
                    val codePoint = specialCharacters.codePointAt(index)
                    assertStringPayloadRoundTrip("before:${String(Character.toChars(codePoint))}:after")
                    index += Character.charCount(codePoint)
                }
            }

            it("successfully serializes and deserializes payload with options before it") {
                val payload = "body: {\"text\":\"Съешь ещё\", \"slash\":\"\\\\\", \"quote\":\"\\\"\"}"
                val source = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.POST)
                source.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionContentFormat, CoAPMessage.MediaType.Json.toInt()))
                source.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionURIPath, "messages"))
                source.payload = CoAPMessagePayload(payload)

                val sourceBytes = CoAPSerializer.toBytes(source)
                assertNotNull(sourceBytes)

                val result = CoAPSerializer.fromBytes(sourceBytes)
                assertNotNull(result)

                assertEquals(payload, result.payload.toString())
                assertEquals(CoAPMessage.MediaType.Json.toInt(), result.getOption(CoAPMessageOptionCode.OptionContentFormat)!!.value)
                assertEquals("messages", result.getOption(CoAPMessageOptionCode.OptionURIPath)!!.value)
            }
        }

        describe("testing options") {

            it("deserializes string option values as UTF-8") {
                val value = "путь/сообщения?text={\"value\":\"ещё\"}&q=a+b"
                val option = CoAPMessageOption(
                    CoAPMessageOptionCode.OptionURIPath,
                    value.toByteArray(StandardCharsets.UTF_8)
                )

                assertEquals(value, option.value)
            }

            it("successfully serializes and deserializes UTF-8 uri path and query options") {
                val source = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET)
                source.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionURIPath, "сообщения"))
                source.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionURIQuery, "text={\"value\":\"ещё\"}"))

                val sourceBytes = CoAPSerializer.toBytes(source)
                assertNotNull(sourceBytes)

                val result = CoAPSerializer.fromBytes(sourceBytes)
                assertNotNull(result)

                assertEquals("сообщения", result.getOption(CoAPMessageOptionCode.OptionURIPath)!!.value)
                assertEquals("text={\"value\":\"ещё\"}", result.getOption(CoAPMessageOptionCode.OptionURIQuery)!!.value)
            }

            it("verifies checksum option when present") {
                val source = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.POST, 0x1234)
                source.token = byteArrayOf(0x01, 0x02, 0x03)
                source.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionURIPath, "checksum"))
                source.payload = CoAPMessagePayload("checksum payload")

                val checksum = CoAPSerializer.checksumForMessage(source)
                source.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionChecksum, checksum))

                val sourceBytes = CoAPSerializer.toBytes(source)
                assertNotNull(sourceBytes)

                val result = CoAPSerializer.fromBytes(sourceBytes)
                assertNotNull(result)

                assertEquals(checksum, result.getOption(CoAPMessageOptionCode.OptionChecksum)!!.value)
                assertEquals("checksum payload", result.payload.toString())
            }

            it("rejects checksum mismatch when checksum option is present") {
                val source = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.POST, 0x1234)
                source.token = byteArrayOf(0x01, 0x02, 0x03)
                source.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionURIPath, "checksum"))
                source.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionChecksum, "00000000"))
                source.payload = CoAPMessagePayload("checksum payload")

                val sourceBytes = CoAPSerializer.toBytes(source)
                assertNotNull(sourceBytes)

                val exception = assertFailsWith(CoAPSerializer.DeserializeException::class) {
                    CoAPSerializer.fromBytes(sourceBytes)
                }
                assertEquals(true, exception.message!!.contains("Checksum mismatch"))
            }

            it("adds checksum option when send flag is enabled") {
                val source = CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.POST, 0x1234)
                source.token = byteArrayOf(0x01, 0x02, 0x03)
                source.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionURIPath, "checksum"))
                source.payload = CoAPMessagePayload("checksum payload")
                source.addChecksumOnSend = true

                val sourceBytes = CoAPSerializer.toBytes(source, addChecksumIfNeeded = true)
                assertNotNull(sourceBytes)

                val result = CoAPSerializer.fromBytes(sourceBytes)
                assertNotNull(result)

                val checksum = result.getOption(CoAPMessageOptionCode.OptionChecksum)!!.value as String
                assertEquals(CoAPSerializer.checksumForMessage(result), checksum)
                assertEquals("checksum payload", result.payload.toString())
            }

            it("successfully serialize and deserialize uri string") {
                val source = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.GET)

                source.setURI("coaps://192.168.1.1:8080/test?param=1&param2=value")

                val sourceBytes = CoAPSerializer.toBytes(source)
                assertNotNull(sourceBytes)

                val result = CoAPSerializer.fromBytes(sourceBytes)
                result!!.address = source.address

                if (result!!.address == null) {
                    com.ndmsystems.coala.helpers.logging.LogHelper.e("Message address == null in CoapSerializerSpek")
                }
                assertNotNull(result)

                assertEquals("coaps://192.168.1.1:8080/test?param=1&param2=value", result.getURI())
            }

        }

        describe("all possible") {
            it("should have valid options") {
                val msg = CoAPSerializer.fromBytes(DummyData.OptionsAllPossible.read())
                assertEquals(25, msg!!.getOptions().size)
            }
        }

        describe("given option value") {
            it("of Int") {
                val msg = CoAPSerializer.fromBytes(DummyData.OptionsIntValue.read())
                val data = msg!!.getOption(CoAPMessageOptionCode.OptionAccept)!!.value
                assertEquals(100, data as? Int ?: 0)
            }

            it("of string") {
                val msg = CoAPSerializer.fromBytes(DummyData.OptionsStringValue.read())
                val data = msg!!.getOption(CoAPMessageOptionCode.OptionAccept)!!.toBytes()
                assertEquals("test", String(data))
            }

            it("of data") {
                val msg = CoAPSerializer.fromBytes(DummyData.OptionsDataValue.read())
                val data = msg!!.getOption(CoAPMessageOptionCode.OptionAccept)!!.toBytes()
                assertArrayEquals("test".toByteArray(), data)
            }

            it("of max Int") {
                val msg = CoAPSerializer.fromBytes(DummyData.OptionsMaxIntValue.read())
                val data = msg!!.getOption(CoAPMessageOptionCode.OptionAccept)!!.value
                assertEquals(Int.MAX_VALUE, data as? Int ?: 0)
            }

            it("of min Int") {
                val msg = CoAPSerializer.fromBytes(DummyData.OptionsMinIntValue.read())
                val data = msg!!.getOption(CoAPMessageOptionCode.OptionAccept)!!.value
                assertEquals(Int.MIN_VALUE, data as? Int ?: 0)
            }
        }

    }

})
