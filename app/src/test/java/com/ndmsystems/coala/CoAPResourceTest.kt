package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPRequestMethod
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * How an incoming request is matched to the handler that should answer it. A loose match answers the
 * wrong request; a strict one leaves a request unanswered until it times out.
 */
object CoAPResourceTest : Spek({

    describe("matching by path") {

        it("matches its own path") {
            assertTrue(resource("info").doesMatch("info"))
        }

        it("does not match a different path") {
            assertFalse(resource("info").doesMatch("status"))
        }

        it("does not match a path that merely contains its own") {
            // A substring match here would route /info-extended at the /info handler.
            assertFalse(resource("info").doesMatch("info-extended"))
        }

        it("is case sensitive, as the wire format is") {
            assertFalse(resource("info").doesMatch("Info"))
        }
    }

    describe("matching by path and method") {

        it("matches when both agree") {
            assertTrue(resource("info", CoAPRequestMethod.GET).doesMatch("info", CoAPRequestMethod.GET))
        }

        it("does not match another method on the same path") {
            assertFalse(resource("info", CoAPRequestMethod.GET).doesMatch("info", CoAPRequestMethod.POST))
        }

        it("does not match the same method on another path") {
            assertFalse(resource("info", CoAPRequestMethod.GET).doesMatch("status", CoAPRequestMethod.GET))
        }
    }

    describe("identity") {

        it("is the path and the method, not the handler") {
            // ResourceRegistry stores by both, so two handlers for the same route are the same slot.
            assertEquals(resource("info", CoAPRequestMethod.GET), resource("info", CoAPRequestMethod.GET))
        }

        it("differs when the method differs") {
            assertNotEquals(resource("info", CoAPRequestMethod.GET), resource("info", CoAPRequestMethod.POST))
        }

        it("differs when the path differs") {
            assertNotEquals(resource("info", CoAPRequestMethod.GET), resource("status", CoAPRequestMethod.GET))
        }

        it("equals itself") {
            val resource = resource("info")

            assertEquals(resource, resource)
        }

        it("is not equal to null") {
            assertFalse(resource("info").equals(null))
        }

        it("is not equal to something else entirely") {
            assertFalse(resource("info").equals("info"))
        }
    }
})

private fun resource(path: String, method: CoAPRequestMethod = CoAPRequestMethod.GET) =
    CoAPResource(method, path, object : CoAPResource.CoAPResourceHandler() {
        override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput =
            CoAPResourceOutput(CoAPMessagePayload("ok"), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain)
    })
