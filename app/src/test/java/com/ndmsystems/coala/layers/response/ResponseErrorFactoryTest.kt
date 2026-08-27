package com.ndmsystems.coala.layers.response

import com.ndmsystems.coala.exceptions.CoAPException
import com.ndmsystems.coala.exceptions.WrongAuthDataException
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Decides whether a response is an answer or a failure, and which failure.
 *
 * Whatever this returns is what the caller sees: `null` means ResponseLayer delivers the payload as
 * a successful response, anything else becomes the error on the caller's handler.
 */
object ResponseErrorFactoryTest : Spek({

    val factory = ResponseErrorFactory()

    describe("a successful response") {

        it("is not an error") {
            assertNull(factory.proceed(response(CoAPMessageCode.CoapCodeContent, """{"ok":true}""")))
        }

        it("is not an error even with no payload") {
            assertNull(factory.proceed(response(CoAPMessageCode.CoapCodeContent, payload = null)))
        }
    }

    describe("a reset") {

        it("is an error whatever the code says") {
            val error = factory.proceed(
                response(CoAPMessageCode.CoapCodeContent, "gone away", type = CoAPMessageType.RST)
            )

            assertNotNull(error)
            assertTrue(error.message!!.contains("gone away"))
        }

        it("gets a stand-in message when it carries nothing") {
            val error = factory.proceed(
                response(CoAPMessageCode.CoapCodeContent, payload = null, type = CoAPMessageType.RST)
            )

            assertTrue(error!!.message!!.contains("Request has been reset"))
        }
    }

    describe("an unauthorized response") {

        it("becomes a WrongAuthDataException when the peer spells it out") {
            val error = factory.proceed(
                response(CoAPMessageCode.CoapCodeBadRequest, "Wrong login or password")
            )

            assertTrue(error is WrongAuthDataException)
        }

        it("becomes a WrongAuthDataException on the code alone") {
            val error = factory.proceed(response(CoAPMessageCode.CoapCodeUnauthorized, payload = null))

            assertTrue(error is WrongAuthDataException)
        }
    }

    describe("an error response carrying a payload error code") {

        it("carries the code and the message through") {
            val error = factory.proceed(
                response(CoAPMessageCode.CoapCodeBadRequest, """{"message":"no such interface","code":7}""")
            )

            assertNotNull(error)
            assertEquals(7, error.payloadErrorCode)
            assertTrue(error.message!!.contains("no such interface"))
        }

        it("copes with a code and no message") {
            val error = factory.proceed(response(CoAPMessageCode.CoapCodeBadRequest, """{"code":7}"""))

            assertEquals(7, error!!.payloadErrorCode)
        }

        it("falls back to the response code when the payload will not parse") {
            // The substring test that routes us here matches plain prose too, so an error whose
            // body is not JSON must still come out as an error - a null here would reach
            // ResponseLayer as "no error" and hand the caller an error body as a good response.
            val error = factory.proceed(
                response(CoAPMessageCode.CoapCodeBadRequest, "error code 7: not json at all")
            )

            assertNotNull(error)
            assertEquals(CoAPMessageCode.CoapCodeBadRequest, error.code)
            assertNull(error.payloadErrorCode, "there was no code to parse out")
        }

        it("still reports a plain success as no error, whatever its payload says") {
            assertNull(factory.proceed(response(CoAPMessageCode.CoapCodeContent, """{"code":0}""")))
        }
    }

    describe("any other error response") {

        it("becomes a plain CoAPException carrying the code") {
            val error = factory.proceed(response(CoAPMessageCode.CoapCodeNotFound, "nothing here"))

            assertNotNull(error)
            assertTrue(error !is WrongAuthDataException)
            assertEquals(CoAPMessageCode.CoapCodeNotFound, error.code)
        }

        it("gets a stand-in message when it carries nothing") {
            val error: CoAPException? = factory.proceed(response(CoAPMessageCode.CoapCodeNotFound, payload = null))

            assertTrue(error!!.message!!.contains("Request has been reset"))
        }
    }
})

private fun response(
    code: CoAPMessageCode,
    payload: String? = null,
    type: CoAPMessageType = CoAPMessageType.ACK
): CoAPMessage = CoAPMessage(type, code).apply {
    address = InetSocketAddress("127.0.0.1", 5683)
    payload?.let { setStringPayload(it) }
}
