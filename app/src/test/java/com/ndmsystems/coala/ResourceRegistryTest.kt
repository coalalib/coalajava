package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPRequestMethod
import io.mockk.mockk
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Routing for resources this peer serves - what the router's own requests to us are matched against.
 * A miss here is a request answered with the wrong handler, or not answered at all.
 */
object ResourceRegistryTest : Spek({

    describe("ResourceRegistry") {

        it("finds a resource by path and method") {
            val registry = ResourceRegistry(mockk(relaxed = true))
            val handler = handler()

            registry.addResource(PATH, CoAPRequestMethod.GET, handler)

            val resource = registry.getResourcesForPath(PATH)?.getResourceByMethod(CoAPRequestMethod.GET)
            assertNotNull(resource)
            assertSame(handler, resource.handler)
        }

        it("keeps different methods on the same path apart") {
            val registry = ResourceRegistry(mockk(relaxed = true))
            val get = handler()
            val post = handler()

            registry.addResource(PATH, CoAPRequestMethod.GET, get)
            registry.addResource(PATH, CoAPRequestMethod.POST, post)

            val group = registry.getResourcesForPath(PATH)!!
            assertSame(get, group.getResourceByMethod(CoAPRequestMethod.GET)!!.handler)
            assertSame(post, group.getResourceByMethod(CoAPRequestMethod.POST)!!.handler)
            assertEquals(2, group.resources.size)
        }

        it("replaces a handler registered twice for the same method") {
            val registry = ResourceRegistry(mockk(relaxed = true))
            val replacement = handler()

            registry.addResource(PATH, CoAPRequestMethod.GET, handler())
            registry.addResource(PATH, CoAPRequestMethod.GET, replacement)

            val group = registry.getResourcesForPath(PATH)!!
            assertEquals(1, group.resources.size)
            assertSame(replacement, group.getResourceByMethod(CoAPRequestMethod.GET)!!.handler)
        }

        it("knows nothing about a path never registered") {
            assertNull(ResourceRegistry(mockk(relaxed = true)).getResourcesForPath(PATH))
        }

        it("removes only the method it was asked to remove") {
            val registry = ResourceRegistry(mockk(relaxed = true))
            registry.addResource(PATH, CoAPRequestMethod.GET, handler())
            registry.addResource(PATH, CoAPRequestMethod.POST, handler())

            registry.removeResource(PATH, CoAPRequestMethod.GET)

            val group = registry.getResourcesForPath(PATH)!!
            assertNull(group.getResourceByMethod(CoAPRequestMethod.GET))
            assertNotNull(group.getResourceByMethod(CoAPRequestMethod.POST))
        }

        it("shrugs when asked to remove a path it never had") {
            ResourceRegistry(mockk(relaxed = true)).removeResource(PATH, CoAPRequestMethod.GET)
        }
    }

    describe("observable resources") {

        it("are found by path") {
            val registry = ResourceRegistry(mockk(relaxed = true))

            registry.addObservableResource(PATH, handler())

            val observable = registry.getObservableResource(PATH)
            assertNotNull(observable)
            assertEquals(CoAPRequestMethod.GET, observable.method)
        }

        it("are not confused with a plain GET on the same path") {
            val registry = ResourceRegistry(mockk(relaxed = true))

            registry.addResource(PATH, CoAPRequestMethod.GET, handler())

            assertNull(registry.getObservableResource(PATH), "a plain resource cannot be observed")
        }

        it("are absent for a path never registered") {
            assertNull(ResourceRegistry(mockk(relaxed = true)).getObservableResource(PATH))
        }
    }
})

private const val PATH = "info"

private fun handler() = object : CoAPResource.CoAPResourceHandler() {
    override fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput =
        CoAPResourceOutput(CoAPMessagePayload("ok"), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain)
}
