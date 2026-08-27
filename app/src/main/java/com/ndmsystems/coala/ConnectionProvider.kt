package com.ndmsystems.coala

import android.net.ConnectivityManager
import com.ndmsystems.coala.Coala.OnPortIsBusyHandler
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.i
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.helpers.logging.LogHelper.w
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.Socket

/**
 * Owns the transport sockets and makes sure only one UDP connect runs at a time, however many
 * callers ask for a connection while it is in flight.
 *
 * Mutable state is guarded by this object's monitor, the same way it was before; coroutines are
 * only ever resumed outside that monitor, so a waiter can never run while the lock is held.
 */
class ConnectionProvider internal constructor(
    private val socketFactory: UdpSocketFactory,
    private val tcpSocketFactory: TcpSocketFactory = RealTcpSocketFactory(),
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    constructor(udpPort: Int, connectivityManager: ConnectivityManager?) :
            this(RealUdpSocketFactory(udpPort, connectivityManager))

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var onPortIsBusyHandler: OnPortIsBusyHandler? = null
    private var connection: MulticastSocket? = null

    /**
     * Resumes every caller waiting on the connect currently in flight; null when none is running.
     * Replaces the `AsyncSubject` the previous implementation used for exactly this job.
     */
    private var pendingConnection: CompletableDeferred<MulticastSocket>? = null

    /** Coroutine backing [pendingConnection], kept so [close] can abort a connect in progress. */
    private var connectJob: Job? = null

    private var tcpSocket: Socket? = null
    private var transportMode: Coala.TransportMode = Coala.TransportMode.UDP
    private var tcpProxyAddress: InetSocketAddress? = null

    /**
     * Returns the open UDP socket, opening one if needed. Concurrent callers share a single
     * connect attempt and are all resumed with its result.
     *
     * @throws IOException when the socket could not be opened, or when [close] dropped the attempt
     * @throws NotImplementedError when called in TCP mode
     */
    suspend fun waitForUdpConnection(): MulticastSocket {
        v("waitForUdpConnection")
        val pending = synchronized(this) {
            when (transportMode) {
                Coala.TransportMode.UDP -> {
                    connection?.let {
                        v("waitForUdpConnection return connection")
                        return it
                    }
                    pendingConnection ?: startConnecting()
                }

                Coala.TransportMode.TCP -> {
                    w("waitForUdpConnection called in TCP mode")
                    throw NotImplementedError("UDP socket not available in TCP mode")
                }
            }
        }
        v("waitForUdpConnection await pending connect")
        return pending.await()
    }

    fun close() {
        val abandoned = synchronized(this) {
            d("close")
            connection?.let {
                if (!it.isClosed) {
                    v("Actual close connection")
                    it.close()
                }
            }
            connection = null

            tcpSocket?.let { if (!it.isClosed) it.close() }
            tcpSocket = null

            connectJob?.cancel()
            connectJob = null
            pendingConnection.also { pendingConnection = null }
        }
        // Outside the monitor: completing the deferred resumes the waiters inline, and they must
        // not run while this object is locked.
        abandoned?.completeExceptionally(IOException("Closed"))
    }

    fun setOnPortIsBusyHandler(onPortIsBusyHandler: OnPortIsBusyHandler?) {
        d("setOnPortIsBusyHandler")
        this.onPortIsBusyHandler = onPortIsBusyHandler
    }

    @Synchronized
    fun getOrCreateTcpSocket(): Socket {
        if (transportMode != Coala.TransportMode.TCP) throw IllegalStateException("Not in TCP mode")
        if (tcpProxyAddress == null) throw IllegalStateException("Tcp proxy address is null")
        if (tcpSocket == null || tcpSocket!!.isClosed) {
            tcpSocket = tcpSocketFactory.connect(tcpProxyAddress!!, TCP_CONNECT_TIMEOUT_MS)
        }
        return tcpSocket!!
    }

    /**
     * Drops the TCP connection so the next [getOrCreateTcpSocket] dials a fresh one.
     *
     * Needed because the local socket does not read as closed when the *proxy* hangs up - reads
     * just hit EOF forever, and a reconnecting loop keeps being handed the same dead socket.
     */
    @Synchronized
    fun invalidateTcpSocket() {
        d("invalidateTcpSocket")
        tcpSocket?.let { if (!it.isClosed) it.close() }
        tcpSocket = null
    }

    @Synchronized
    fun setTransportMode(transportMode: Coala.TransportMode, tcpProxyAddress: InetSocketAddress?) {
        this.transportMode = transportMode
        this.tcpProxyAddress = tcpProxyAddress
        close()
    }

    /**
     * Starts the single connect attempt every current and future waiter will share.
     *
     * Caller must hold this object's monitor: that is what keeps [connectJob] from being cleared
     * by the coroutine below before it has even been assigned - a race the previous
     * `timerSubscription` handling was open to, which could wedge the provider into a state where
     * it refused to ever reconnect.
     */
    private fun startConnecting(): CompletableDeferred<MulticastSocket> {
        v("waitForUdpConnection initConnection")
        val deferred = CompletableDeferred<MulticastSocket>()
        pendingConnection = deferred
        connectJob = scope.launch {
            try {
                val socket = connectWithRetries()
                val isStillWanted = synchronized(this@ConnectionProvider) {
                    if (pendingConnection === deferred) {
                        d("saveConnection")
                        connection = socket
                        pendingConnection = null
                        connectJob = null
                        true
                    } else {
                        false
                    }
                }
                if (isStillWanted) {
                    deferred.complete(socket)
                } else {
                    // close() or setTransportMode() dropped this attempt while the socket was
                    // being opened. Nobody owns it now, and the waiters have already been failed.
                    d("Connect finished after close, discarding socket")
                    socket.close()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                i("Can't init connection: ${error.message}")
                val isStillWanted = synchronized(this@ConnectionProvider) {
                    if (pendingConnection === deferred) {
                        pendingConnection = null
                        connectJob = null
                        true
                    } else {
                        false
                    }
                }
                if (isStillWanted) {
                    deferred.completeExceptionally(error)
                    // Inside the guard: an attempt close() already abandoned has nobody to tell.
                    // Firing the busy-port handler for it would trigger the app's recovery path
                    // during a deliberate shutdown.
                    invokePortIsBusyIfNeeded()
                }
            }
        }
        return deferred
    }

    /**
     * One initial attempt plus [CONNECT_RETRIES] retries - the same budget the previous
     * `Observable.retry(3)` allowed.
     */
    private suspend fun connectWithRetries(): MulticastSocket {
        var lastError: Throwable = IOException(CANT_CREATE_CONNECTION)
        repeat(CONNECT_RETRIES + 1) { attempt ->
            // Rx stopped retrying once the subscription was disposed; cancellation is the
            // equivalent here, and close() relies on it to stop a doomed reconnect loop.
            currentCoroutineContext().ensureActive()
            try {
                return socketFactory.create() ?: throw IOException(CANT_CREATE_CONNECTION)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                v("Connect attempt ${attempt + 1} failed: ${error.message}")
                lastError = error
            }
        }
        throw lastError
    }

    private fun invokePortIsBusyIfNeeded() {
        onPortIsBusyHandler?.onPortIsBusy()
    }

    companion object {
        private const val CONNECT_RETRIES = 3
        private const val TCP_CONNECT_TIMEOUT_MS = 2000
        private const val CANT_CREATE_CONNECTION = "Can't create connection"
    }
}
