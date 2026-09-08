package com.ndmsystems.coala

import android.net.ConnectivityManager
import android.os.Build
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogKeys
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketException
import java.net.UnknownHostException

/**
 * Creates the UDP socket that [ConnectionProvider] hands out.
 *
 * Split out of [ConnectionProvider] so that its retry / single-flight logic can be unit tested
 * without opening real sockets - the provider itself no longer knows how a socket is built.
 */
internal fun interface UdpSocketFactory {

    /**
     * @return a bound socket, or null when it could not be created at all. Callers treat null the
     * same way they treat a thrown [IOException]: as a failed attempt.
     */
    @Throws(IOException::class)
    fun create(): MulticastSocket?
}

/**
 * Production implementation: binds a [MulticastSocket] on [udpPort], preferring the currently
 * active network, and falls back to a reuse-address bind when the port is already taken.
 */
internal class RealUdpSocketFactory(
    private val udpPort: Int,
    private val connectivityManager: ConnectivityManager?
) : UdpSocketFactory {

    @Throws(IOException::class)
    override fun create(): MulticastSocket? {
        // Held outside the try so a socket that binds but fails to configure can be closed before
        // the fallback runs - leaking it keeps the requested port occupied for the retry.
        var socket: MulticastSocket? = null
        return try {
            val s = MulticastSocket(udpPort) //Don't change to 5683 or Samsung on wifi stop working!
            socket = s
            // IMPORTANT: socket is not connected yet → can bind to network
            bindToActiveNetwork(s)
            s.receiveBufferSize = RECEIVE_BUFFER_SIZE
            s.trafficClass = IPTOS_RELIABILITY or IPTOS_THROUGHPUT or IPTOS_LOWDELAY
            LogHelper.d(
                "createConnection",
                mapOf("udp_port" to udpPort, "port" to s.port, "local_port" to s.localPort)
            )
            socket = null
            s
        } catch (ex: SocketException) {
            socket?.let { runCatching { it.close() } }
            LogHelper.i(
                "MulticastSocket can't be created, SocketException, trying to reuse",
                mapOf(LogKeys.ERROR_TYPE to ex.javaClass.name, LogKeys.ERROR to ex.localizedMessage)
            )
            tryToReuseSocket()
        } catch (ex: Exception) {
            socket?.let { runCatching { it.close() } }
            LogHelper.e(
                "MulticastSocket can't be created",
                mapOf(LogKeys.ERROR_TYPE to ex.javaClass.name, LogKeys.ERROR to ex.localizedMessage)
            )
            tryToReuseSocket()
        }
    }

    private fun bindToActiveNetwork(socket: DatagramSocket) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val net = connectivityManager?.activeNetwork ?: return
                // Platform requirement: socket must not be connected; bound is OK.
                net.bindSocket(socket)
                LogHelper.d("Socket bound to active network", mapOf("network" to net.toString()))
            } catch (t: Throwable) {
                LogHelper.w(
                    "bindToActiveNetwork failed",
                    mapOf(LogKeys.ERROR_TYPE to t.javaClass.simpleName, LogKeys.ERROR to t.message)
                )
            }
        }
    }

    private fun tryToReuseSocket(): MulticastSocket? {
        LogHelper.d("tryToReuseSocket")
        return try {
            val srcAddress = InetSocketAddress(udpPort)
            val connection = MulticastSocket(null)
            connection.reuseAddress = true
            connection.trafficClass = IPTOS_RELIABILITY or IPTOS_THROUGHPUT or IPTOS_LOWDELAY
            connection.receiveBufferSize = RECEIVE_BUFFER_SIZE
            connection.bind(srcAddress)
            LogHelper.w(
                "MulticastSocket reused",
                mapOf(
                    "receive_buffer_size" to connection.receiveBufferSize,
                    "bound" to connection.isBound,
                    "closed" to connection.isClosed,
                    "connected" to connection.isConnected
                )
            )
            connection
        } catch (ex: SocketException) {
            LogHelper.w(
                "MulticastSocket can't be created, and can't be reused",
                mapOf(LogKeys.ERROR_TYPE to ex.javaClass.name, LogKeys.ERROR to ex.localizedMessage)
            )
            null
        } catch (e: UnknownHostException) {
            LogHelper.w(
                "MulticastSocket can't be created, and can't be reused: UnknownHostException",
                mapOf(LogKeys.ERROR to e.localizedMessage)
            )
            null
        } catch (e: IOException) {
            LogHelper.w(
                "MulticastSocket can't be created, and can't be reused: IOException",
                mapOf(LogKeys.ERROR to e.localizedMessage)
            )
            null
        }
    }

    companion object {
        private const val RECEIVE_BUFFER_SIZE = 1048576
        private const val IPTOS_RELIABILITY = 0x04
        private const val IPTOS_THROUGHPUT = 0x08
        private const val IPTOS_LOWDELAY = 0x10
    }
}
