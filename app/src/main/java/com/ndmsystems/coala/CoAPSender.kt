package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.i
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.layers.LogLayer.Companion.getStringToPrintSendingMessage
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.MulticastSocket

class CoAPSender(
    private val connectionProvider: ConnectionProvider,
    private val messagePool: CoAPMessagePool,
    private val layersStack: LayersStack,
    /**
     * Where the sending loop runs. Seam for tests: under a test dispatcher the loop's polling
     * delay becomes virtual time, so a scenario can drive it turn by turn instead of sleeping.
     */
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Volatile: the sending loop reads it on its way out, outside this object's monitor. */
    @Volatile
    var isStarted = false
        private set
    private var sendingJob: Job? = null

    /** Volatile: written by whichever thread opened the socket, read by the sending loop. */
    @Volatile
    private var connection: MulticastSocket? = null

    /** The coroutine waiting on the socket, kept so [stop] can retire it and a late connect is ignored. */
    private var connectWaiter: Job? = null
    private var transportMode: Coala.TransportMode = Coala.TransportMode.UDP

    /**
     * Unconfined so that waiting for the socket behaves exactly like the `Single.subscribe()` it
     * replaces: it runs inline on the calling thread when a connection is already open, and
     * resumes on the connecting thread when it is not. [Coala.isStarted] is read straight after
     * [start], so the fast path has to stay synchronous. The loop itself is launched onto
     * [workDispatcher], not this.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Synchronized
    fun start() {
        v("CoAPSender start with mode $transportMode")

        if (transportMode == Coala.TransportMode.UDP) {
            if (connection == null) {
                if (connectWaiter?.isActive != true) {
                    // LAZY, then assign, then start: with Unconfined an eager launch would run the
                    // body inline before the assignment, and the identity check below would take
                    // its own waiter for a retired one.
                    val waiter = scope.launch(start = CoroutineStart.LAZY) {
                        val self = coroutineContext.job
                        try {
                            onUdpSocketStarted(self, connectionProvider.waitForUdpConnection())
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            e("Can't start CoAPSender: $error")
                        }
                    }
                    connectWaiter = waiter
                    waiter.start()
                }
            } else {
                // The socket is only cleared by stop(), so getting here means the sending loop
                // died on its own. Without this the sender would stay dead for good and the pool
                // would never be drained again. Mirrors CoAPReceiver.start().
                startSendingLoop()
            }
        } else {
            i("CoAPSender TCP mode try to start if needed")
            startSendingLoop()
        }
    }

    /**
     * Brings the sender up on the socket the connect delivered - unless [stop] retired the waiter
     * while the connect was in flight, in which case the socket belongs to nobody and starting a
     * loop for it would revive a sender that was deliberately shut down.
     */
    @Synchronized
    private fun onUdpSocketStarted(waiter: Job, newConnection: MulticastSocket?) {
        if (connectWaiter !== waiter) {
            d("Connect finished after the sender was stopped, ignoring the socket")
            return
        }
        connectWaiter = null
        d("CoAPSender started, socket: $newConnection")
        connection = newConnection
        startSendingLoop()
    }

    @Synchronized
    private fun startSendingLoop() {
        val needsNewLoop = !isStarted || sendingJob?.isCompleted == true
        // Set before the loop is launched, not after: a loop that gets going first would otherwise
        // be able to observe isStarted == false and take itself for already retired.
        isStarted = true
        if (needsNewLoop) {
            v("Sending loop try to start")
            // Assigned under the monitor, and everything the loop does to this field is taken
            // under the same monitor, so the loop cannot clear a field it has not been given yet.
            sendingJob = scope.launch(workDispatcher) {
                val self = coroutineContext.job
                try {
                    runSendingLoop()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    i("Sending loop stopped by ${error.javaClass.simpleName}: ${error.message}")
                }
                v("Sending loop end")
                // Reached only when the loop came back without the job being cancelled - a
                // deliberate stop() cancels it, and this delay then throws straight through
                // instead of reviving a sender somebody asked to shut down.
                delay(RESTART_DELAY_MS)
                d("Try to start sending loop")
                restartIfStillCurrent(self)
            }
        }
    }

    /**
     * Restarts the sender, but only if [job] is still the one it is running.
     *
     * A loop on its way out has no claim on the sender any more: while it was sitting out its
     * restart delay, [stop] may have shut the sender down and [start] may have put a replacement
     * in place. Taking the decision here - after the delay and under this object's monitor - is
     * what keeps a retired loop from clearing its successor's state or from reviving a sender that
     * was deliberately stopped.
     */
    @Synchronized
    private fun restartIfStillCurrent(job: Job) {
        if (!isStarted || sendingJob !== job) {
            d("Sending loop was retired while waiting to restart, leaving it alone")
            return
        }
        sendingJob = null
        isStarted = false
        start()
    }

    @Synchronized
    fun stop() {
        isStarted = false
        // The waiter too, not just the loop: a connect resolving after stop() would otherwise
        // deliver its socket to onUdpSocketStarted and revive the sender.
        connectWaiter?.cancel()
        connectWaiter = null
        sendingJob?.cancel()
        sendingJob = null
        layersStack.onStop()
        connection = null
    }

    /**
     * Drains [CoAPMessagePool] until the job is cancelled.
     *
     * Cancellation is the only way out: every exit the thread version had was driven by its own
     * interrupt flag, and the loop below leaves through the same door via [delay] or the `isActive`
     * check. Returning without being cancelled therefore means something went wrong, which is what
     * the caller's restart is for.
     */
    private suspend fun runSendingLoop() {
        v("Sending loop start, number in pool: ${messagePool.size()}")
        while (currentCoroutineContext().isActive) {
            val message = messagePool.next()
            if (message == null) {
                // Empty queue? Wait for some milliseconds...
                delay(IDLE_POLL_MS)
                continue
            }
            try {
                val destinationAddressReference = Reference(message.address)
                // Hack to preserve original destination address before layers rewrite it
                message.address = destinationAddressReference.get()
                if (message.address == null) {
                    e("Message address == null in sending loop")
                }
                // Run Layers Chain
                val layerResult = try {
                    layersStack.onSend(message, destinationAddressReference)
                } catch (e: LayersStack.InterruptedException) {
                    d("Sending loop interrupted while running layers: ${e.message}")
                    continue
                } catch (ex: Exception) {
                    i("Exception in sending loop layers: ${ex.message}, ${Hex.encodeHexString(message.token)}")
                    continue
                }
                val messageForSend = layerResult.message ?: message
                if (destinationAddressReference.get() == null) {
                    e(
                        "Destination is null!! isNeedToSend = " + layerResult.shouldContinue + ", message = " + getStringToPrintSendingMessage(
                            messageForSend,
                            destinationAddressReference
                        )
                    )
                } else {
                    if (destinationAddressReference.get().toString().contains("local")) {
                        e("Try to send to localhost!!!")
                    }
                }

                // send it now!
                if (layerResult.shouldContinue) {
                    if (destinationAddressReference.get() == null) {
                        e(
                            "Destination is null, but need to sending, message = " + getStringToPrintSendingMessage(
                                messageForSend,
                                destinationAddressReference
                            )
                        )
                    } else {
                        v("message id ${message.id}, token ${Hex.encodeHexString(message.token)} actual sending to ${destinationAddressReference.get()}")
                        sendMessageToAddress(destinationAddressReference.get(), messageForSend)
                    }
                }

                // post-process
                if (messageForSend.type != CoAPMessageType.CON) {
                    // we can remove this message from Pool right away if it's not CON
                    messagePool.remove(messageForSend)
                }
            } catch (e: IOException) {
                d("IOException: " + e.message)
                // A broken pipe to the proxy: our end still reads as open, so drop it or every
                // retry keeps writing into the same dead connection.
                if (transportMode == Coala.TransportMode.TCP) connectionProvider.invalidateTcpSocket()
                delay(IDLE_POLL_MS)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Per message, like the IOException above, so one poisoned message (an unresolved
                // address NPE, a misconfigured TCP proxy) does not kill the whole loop and put it
                // into a silent 500 ms restart cycle. The message keeps being offered until its
                // attempt budget evicts it, which bounds the noise.
                e("Unexpected error sending message ${message.id}: ${error.javaClass.simpleName} ${error.message}")
                delay(IDLE_POLL_MS)
            }
        }
        i("Sending loop stopped")
    }

    @Throws(IOException::class)
    private fun sendMessageToAddress(address: InetSocketAddress?, message: CoAPMessage) {
        val messageData = CoAPSerializer.toBytes(message, addChecksumIfNeeded = true)
        if (transportMode == Coala.TransportMode.UDP) {
            var udpPacket: DatagramPacket? = null
            if (messageData != null) {
                try {
                    udpPacket = DatagramPacket(messageData, messageData.size, address)
                } catch (exception: IllegalArgumentException) {
                    e("sendMessageToAddress IllegalArgumentException, address: " + address.toString())
                }
            }
            // Send data!
            if (connection != null && udpPacket != null) {
                connection!!.send(udpPacket)
            }
        } else if (transportMode == Coala.TransportMode.TCP) {
            d("CoAPSender: sending via TCP socket")
            if (messageData != null && address != null) {
                val out = connectionProvider.getOrCreateTcpSocket().getOutputStream()
                out.write(TcpFraming.encode(address, messageData))
                out.flush()
            }
        }
    }

    fun setTransportMode(mode: Coala.TransportMode) {
        if (transportMode == mode) return
        stop()
        // Recreate sender/receiver for new mode; same connectionProvider
        transportMode = mode
        start()
    }

    companion object {
        /** How long a dead sending loop waits before trying to bring the sender back up. */
        internal const val RESTART_DELAY_MS = 500L

        /** How long the loop waits before asking the pool again when it had nothing to send. */
        internal const val IDLE_POLL_MS = 50L
    }
}
