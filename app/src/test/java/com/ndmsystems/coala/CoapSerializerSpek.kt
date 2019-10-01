package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import org.junit.Assert.assertArrayEquals
import org.spekframework.spek2.Spek
import org.spekframework.spek2.dsl.Skip
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull


/*
 * Created by Evgenii Stepanov on 02.09.19
 */
object CoapSerializerSpek : Spek({

    val loremText = "Съешь ещё этих мягких французских булок, да выпей же чаю."
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
                        result = CoAPSerializer.toBytes(message)
                        assertNotNull(result)
                    }

                    it("deserialization successful with message type $expectedValue") {
                        val result = CoAPSerializer.fromBytes(result)
                        assertEquals(expectedValue, result.type)
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
                        result = CoAPSerializer.toBytes(message)
                        assertNotNull(result)
                    }
                    it("deserialization successful with message type ${expectedValue.name}") {
                        val result = CoAPSerializer.fromBytes(result)
                        assertEquals(expectedValue, result.code)
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
                        result = CoAPSerializer.toBytes(message)
                        assertNotNull(result)
                    }

                    it("deserialization successful with message id $expectedValue") {
                        val result = CoAPSerializer.fromBytes(result)
                        assertEquals(expectedValue, result.id)
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
                assertArrayEquals(byteArrayOf(125, 27, 117, 127, -76, 2, 0, -41), result.token)
            }


        }

        //done
        describe("testing payload") {
            it("successfully read payload") {
                var binaryMessage = byteArrayOf(0b01000000.toByte(), 1, 0, 0)
                binaryMessage += 0xff.toByte()
                binaryMessage += loremText.toByteArray()

                val result = CoAPSerializer.fromBytes(binaryMessage)

                assertEquals(loremText, result.payload.toString())
            }


            //todo
            //   The presence of a
            //   marker followed by a zero-length payload MUST be processed as a
            //   message format error.
            //   у нас ничего не происходит, а должно ли?
            it("deserialization failed when payload marker presence but payload is zero length", skip = Skip.Yes()) {
                val binaryMessage = byteArrayOf(0b01000000.toByte(), 1, 0, 0, 255.toByte())
                val exception = assertFailsWith(CoAPSerializer.DeserializeException::class) {
                    CoAPSerializer.fromBytes(binaryMessage)
                }
            }

        }


        describe("testing options") {

            it("successfully serialize and deserialize uri string") {
                val source = CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.GET)

                source.uri = "coaps://192.168.1.1:8080/test?param=1&param2=value"

                val sourceBytes = CoAPSerializer.toBytes(source)
                assertNotNull(sourceBytes)

                val result = CoAPSerializer.fromBytes(sourceBytes)
                result.destination = source.destination
                assertNotNull(result)

                assertEquals("coaps://192.168.1.1:8080/test?param=1&param2=value", result.uri)
            }

        }

    }

})