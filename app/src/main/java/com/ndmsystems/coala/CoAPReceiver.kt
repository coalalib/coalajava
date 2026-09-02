package com.ndmsystems.coala

import com.ndmsystems.coala.CoAPSerializer.DeserializeException
import com.ndmsystems.coala.CoAPSerializer.fromBytes
import com.ndmsystems.coala.helpers.Hex
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.i
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.layers.arq.states.LoggableState
import com.ndmsystems.coala.message.CoAPMessage
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

class CoAPReceiver(
    private val connectionProvider: ConnectionProvider,
    private val receiveLayerStack: LayersStack,
    /**
     * Where the receiving loop runs. Defaults to [Dispatchers.IO] because the loop parks in a
     * blocking `DatagramSocket.receive()` - that is what IO exists for, and the socket being closed
     * is what releases the thread, cancellation alone cannot. Seam for tests.
     */
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /** Volatile: the receiving loop loops on it and reads it on its way out, outside this monitor. */
    @Volatile
    var isStarted = false
        private set
    private var receivingJob: Job? = null

    /** Volatile: written by whichever thread opened the socket, read by the receiving loop. */
    @Volatile
    private var connection: MulticastSocket? = null

    /** The coroutine waiting on the socket, kept so [stop] can retire it and a late connect is ignored. */
    private var connectWaiter: Job? = null
    private var tcpReceivingJob: Job? = null
    private var transportMode: Coala.TransportMode = Coala.TransportMode.UDP

    /** Unconfined for the same reason as [CoAPSender.scope]: keep the already-connected path inline. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Synchronized
    fun start() {
        v("CoAPReceiver start with mode $transportMode")
        when (transportMode) {
            Coala.TransportMode.UDP -> {
                if (connection == null) {
                    if (connectWaiter?.isActive != true) {
                        // LAZY, then assign, then start - see CoAPSender.start() for why.
                        val waiter = scope.launch(start = CoroutineStart.LAZY) {
                            val self = coroutineContext.job
                            try {
                                onUdpSocketStarted(self, connectionProvider.waitForUdpConnection())
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                e("Can't start CoAPReceiver: $error")
                            }
                        }
                        connectWaiter = waiter
                        waiter.start()
                    }
                } else {
                    startReceivingLoop()
                }
            }

            Coala.TransportMode.TCP -> {
                d("CoAPReceiver TCP mode start if needed")
                startTcpReceivingLoop()
            }
        }
    }

    /** See [CoAPSender.onUdpSocketStarted]: a socket delivered after [stop] belongs to nobody. */
    @Synchronized
    private fun onUdpSocketStarted(waiter: Job, newConnection: MulticastSocket?) {
        if (connectWaiter !== waiter) {
            d("Connect finished after the receiver was stopped, ignoring the socket")
            return
        }
        connectWaiter = null
        v("CoAPReceiver started, socket: $newConnection")
        connection = newConnection
        startReceivingLoop()
    }

    @Synchronized
    private fun startReceivingLoop() {
        val needsNewLoop = !isStarted || receivingJob?.isCompleted == true
        // Set before the loop is launched, not after: the loop reads isStarted, so one that gets
        // going first would see false and quit before receiving anything.
        isStarted = true
        if (needsNewLoop) {
            v("Receiving loop try to start")
            receivingJob = scope.launch(workDispatcher) {
                val self = coroutineContext.job
                try {
                    runReceivingLoop()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    i("Receiving loop stopped by ${error.javaClass.simpleName}: ${error.message}")
                }
                v("Receiving loop end")
                // Reached only when the loop came back without the job being cancelled - a
                // deliberate stop() cancels it, and this delay then throws straight through
                // instead of reviving a receiver somebody asked to shut down.
                delay(RESTART_DELAY_MS)
                d("Try to start receiving loop")
                restartIfStillCurrent(self)
            }
        }
    }

    /**
     * Restarts the receiver, but only if [job] is still the one it is running.
     *
     * A loop on its way out has no claim on the receiver any more: while it was sitting out its
     * restart delay, [stop] may have shut the receiver down and [start] may have put a replacement
     * in place. Taking the decision here - after the delay and under this object's monitor - is
     * what keeps a retired loop from clearing its successor's state or from reviving a receiver
     * that was deliberately stopped.
     */
    @Synchronized
    private fun restartIfStillCurrent(job: Job) {
        if (!isStarted || receivingJob !== job) {
            d("Receiving loop was retired while waiting to restart, leaving it alone")
            return
        }
        receivingJob = null
        isStarted = false
        start()
    }

    @Synchronized
    fun stop() {
        i("CoAPReceiver stop")
        isStarted = false
        // The waiter too - a connect resolving after stop() must not revive the receiver.
        connectWaiter?.cancel()
        connectWaiter = null
        // Cancelling only marks the loops: they are parked in a blocking read and come back when
        // ConnectionProvider.close() shuts the socket, which Coala.stop() does right after this.
        receivingJob?.cancel()
        receivingJob = null
        // The TCP loop used to be left running here entirely - stop() never touched its thread, so
        // only the socket closing ended it, and the Thread reference was never cleared.
        tcpReceivingJob?.cancel()
        tcpReceivingJob = null
        connection = null
        // The receive stack's layers get the same shutdown signal the sender gives the send
        // stack - shared layers are cleared twice, which every onStop() tolerates.
        receiveLayerStack.onStop()
    }

    fun getReceivedStateForToken(token: ByteArray?): LoggableState? {
        return receiveLayerStack.getArqReceivedStateForToken(token!!)
    }

    /**
     * Reads datagrams and feeds them to the receive stack until the job is cancelled or the socket
     * goes away.
     *
     * `receive()` blocks with no timeout, so cancelling alone does not bring this back - the socket
     * being closed is what does, which is why [Coala.stop] closes the connection right after
     * stopping the receiver.
     */
    private suspend fun runReceivingLoop() {
        v("Receiving loop start")
        while (currentCoroutineContext().isActive && isStarted) {

            // prepare udp packer
            val input = ByteArray(MAX_DATAGRAM_SIZE)
            val udpPacket = DatagramPacket(input, input.size)

            // Reading from UDP
            try {
                val socket = connection
                if (socket != null && !socket.isClosed) socket.receive(udpPacket) else break
            } catch (e: IOException) {
                d("IOException when try to receive message: ${e.message}")
                continue
            }
            if (!currentCoroutineContext().isActive || !isStarted) {
                d("cancelled = ${!currentCoroutineContext().isActive} isRunning = $isStarted stopping")
                break
            }

            val socketAddress = try {
                udpPacket.socketAddress as InetSocketAddress
            } catch (e: IllegalArgumentException) {
                LogHelper.w("IllegalArgumentException when try to get message address: ${e.message}")
                continue
            }

            // Build message from bytes
            val message = getMessageFromPacket(udpPacket, socketAddress) ?: continue
            if (message.id < 0) {
                e("CoAPReceiver: Receiving data from CoAP Peer: Invalid Data. Skipping.")
                continue
            }

            // Run Layers Chain
            try {
                v("message id ${message.id}, token ${Hex.encodeHexString(message.token)} actual received from ${socketAddress}, send to layers")
                val senderAddressReference = Reference(socketAddress)
                message.address = senderAddressReference.get()
                if (message.address == null) {
                    e("Message address == null in receiving loop")
                }
                receiveLayerStack.onReceive(message, senderAddressReference)
            } catch (e: LayersStack.InterruptedException) {
                d("Receiving loop interrupted while running layers: ${e.message}")
            } catch (ex: Exception) {
                i("Exception in receiving loop layers: ${ex.message}, ${LogHelper.getShortStackTraceString(ex)}")
                continue
            }
        }
        i("Receiving loop stopped")
    }

    private fun getMessageFromPacket(udpPacket: DatagramPacket, addressFrom: InetSocketAddress? = null): CoAPMessage? {
        val data = ByteArray(udpPacket.length)
        System.arraycopy(udpPacket.data, udpPacket.offset, data, 0, udpPacket.length)
        val message: CoAPMessage? = try {
            fromBytes(data, addressFrom)
        } catch (e: DeserializeException) {
            // Debug, not error: an open UDP socket receives unsolicited internet traffic - STUN
            // probes, scanners, stray packets - and failing to parse those says nothing about the
            // app. The reported senders bear that out (e.g. :3478 STUN). A parse failure on the
            // TCP path below is a different matter, since that is an established connection to our
            // own server, and stays at error level.
            d("Deserialization error: " + e.message)
            if (BuildConfig.DEBUG) e.printStackTrace()
            return null
        }
        return message
    }

    @Synchronized
    private fun startTcpReceivingLoop() {
        if (tcpReceivingJob?.isCompleted != false) {
            d("startTcpReceivingLoop, make new loop")
            isStarted = true
            tcpReceivingJob = scope.launch(workDispatcher) {
                val self = coroutineContext.job
                try {
                    runTcpReceivingLoop()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: LayersStack.InterruptedException) {
                    d("TCP receiving loop interrupted: ${e.message}")
                } catch (e: Exception) {
                    // A read that fails after stop() cancelled us is how this loop is *supposed* to
                    // end: it is parked in a blocking read, and ConnectionProvider.close() shutting
                    // the socket is the only thing that wakes it. That is a shutdown, not an error,
                    // and it happens every time the app goes to background.
                    if (isActive) {
                        LogHelper.e("TCP receiving loop error: ${e.message}, ${LogHelper.getShortStackTraceString(e)}")
                        // EOF from the proxy does not close our end of the socket, so without this
                        // the restarted loop is handed the same dead connection and dies again.
                        connectionProvider.invalidateTcpSocket()
                    } else {
                        d("TCP receiving loop ended with the socket: ${e.message}")
                    }
                }
                i("TCP receiving loop stopped")
                // The same self-healing the UDP loop and the sender have: the proxy dropping the
                // connection must not leave a receiver that reports isStarted yet hears nothing -
                // requests kept going out while every answer was lost until a transport bounce.
                delay(RESTART_DELAY_MS)
                d("Try to restart TCP receiving loop")
                restartTcpIfStillCurrent(self)
            }
        }
    }

    /** [restartIfStillCurrent], for the TCP loop and its own job field. */
    @Synchronized
    private fun restartTcpIfStillCurrent(job: Job) {
        if (!isStarted || tcpReceivingJob !== job) {
            d("TCP receiving loop was retired while waiting to restart, leaving it alone")
            return
        }
        tcpReceivingJob = null
        isStarted = false
        start()
    }

    /**
     * Reads framed messages off the proxy connection.
     *
     * Like the UDP loop this parks in a blocking read, so cancelling marks it and the socket being
     * closed is what actually brings it back.
     */
    private suspend fun runTcpReceivingLoop() {
        val input = connectionProvider.getOrCreateTcpSocket().getInputStream()
        while (currentCoroutineContext().isActive && isStarted) {
            val frame = TcpFraming.decode(input) ?: continue // not our frame
            val message = try {
                fromBytes(frame.payload, frame.address)
            } catch (e: Exception) {
                e("TCP frame parse error: ${e.message}")
                null
            }
            d("Received from tcp socket $message")
            if (message != null) {
                val senderAddressReference = Reference(frame.address)
                message.address = senderAddressReference.get()
                receiveLayerStack.onReceive(message, senderAddressReference)
            }
        }
    }

    /** Switches the mode only; see [CoAPSender.setTransportMode] for why it does not restart. */
    fun setTransportMode(mode: Coala.TransportMode) {
        if (transportMode == mode) return
        transportMode = mode
    }

    companion object {
        const val TAG = "CoAPReceiver"

        /** How long a dead receiving loop waits before trying to bring the receiver back up. */
        internal const val RESTART_DELAY_MS = 500L

        /** Per-datagram read buffer - not SO_RCVBUF, which RealUdpSocketFactory sets to 1 MiB. */
        private const val MAX_DATAGRAM_SIZE = 4096
    }
}