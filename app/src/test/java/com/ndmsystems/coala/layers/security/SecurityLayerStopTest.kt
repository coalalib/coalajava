package com.ndmsystems.coala.layers.security

import com.ndmsystems.coala.AckHandlersPool
import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.crypto.CurveRepository
import com.ndmsystems.coala.exceptions.BaseCoalaThrowable
import com.ndmsystems.coala.exceptions.CoalaStoppedException
import com.ndmsystems.coala.helpers.CoalaHelper
import com.ndmsystems.coala.layers.response.ResponseData
import com.ndmsystems.coala.layers.response.ResponseHandler
import com.ndmsystems.coala.layers.security.session.SecuredSessionPool
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import io.mockk.every
import io.mockk.mockk
import java.net.InetSocketAddress
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A request parked behind a handshake when coala stops. Coala.stop() clears the handler pool
 * asynchronously and then runs the layers' onStop() synchronously, so either side of that race
 * can come first; the request has to be answered once whichever does.
 */
class SecurityLayerStopTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val ackHandlersPool = AckHandlersPool(clearDispatcher = dispatcher)
    private var clientHelloHandler: CoAPHandler? = null
    private val client = mockk<CoAPClient>(relaxed = true).also {
        // What Coala.send does with a handler: registers it under the message id.
        every { it.send(any(), any()) } answers {
            val handler = secondArg<CoAPHandler?>() ?: return@answers
            clientHelloHandler = handler
            ackHandlersPool.add(firstArg<CoAPMessage>().id, handler)
        }
    }
    private val securityLayer = SecurityLayer(
        mockk<CoAPMessagePool>(relaxed = true),
        ackHandlersPool,
        client,
        SecuredSessionPool(),
        CurveRepository(CoalaHelper.storage),
        pendingErrorDispatcher = dispatcher
    )

    private val peer = InetSocketAddress("192.0.2.10", 5683)
    private val errors = mutableListOf<BaseCoalaThrowable>()
    private val request = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST).apply {
        setURIScheme(CoAPMessage.Scheme.SECURE)
        address = peer
        token = byteArrayOf(1, 2, 3, 4)
        responseHandler = object : ResponseHandler {
            override fun onResponse(responseData: ResponseData) {}
            override fun onError(error: BaseCoalaThrowable) {
                errors += error
            }
        }
    }

    @Test
    fun `a parked request is failed as stopped when onStop runs before the hello fails`() {
        securityLayer.onSend(request, Reference(peer))

        ackHandlersPool.clear(CoalaStoppedException("Coala stopped"))
        securityLayer.onStop()
        scheduler.advanceUntilIdle()

        assertEquals(1, errors.size)
        assertTrue(errors.single() is CoalaStoppedException)
    }

    @Test
    fun `a parked request is answered once when the hello fails before onStop runs`() {
        securityLayer.onSend(request, Reference(peer))

        clientHelloHandler!!.onAckError("Coala stopped")
        scheduler.advanceUntilIdle()
        securityLayer.onStop()
        scheduler.advanceUntilIdle()

        assertEquals(1, errors.size)
    }
}
