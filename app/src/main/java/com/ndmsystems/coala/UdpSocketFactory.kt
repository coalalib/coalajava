package com.ndmsystems.coala

import android.net.ConnectivityManager
import android.os.Build
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.i
import com.ndmsystems.coala.helpers.logging.LogHelper.w
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
        return try {
            val s = MulticastSocket(udpPort) //Don't change to 5683 or Samsung on wifi stop working!
            // IMPORTANT: socket is not connected yet → can bind to network
            bindToActiveNetwork(s)
            s.receiveBufferSize = RECEIVE_BUFFER_SIZE
            s.trafficClass = IPTOS_RELIABILITY or IPTOS_THROUGHPUT or IPTOS_LOWDELAY
            d("createConnection, 'udpPort' is $udpPort, port = ${s.port}, localPort = ${s.localPort}. ")
            s
        } catch (ex: SocketException) {
            i("MulticastSocket can't be created, SocketException, try to reuse: " + ex.javaClass + " " + ex.localizedMessage)
            tryToReuseSocket()
        } catch (ex: Exception) {
            e("MulticastSocket can't be created: " + ex.javaClass + " " + ex.localizedMessage)
            tryToReuseSocket()
        }
    }

    private fun bindToActiveNetwork(socket: DatagramSocket) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val net = connectivityManager?.activeNetwork ?: return
                // Platform requirement: socket must not be connected; bound is OK.
                net.bindSocket(socket)
                d("Socket bound to active network: $net")
            } catch (t: Throwable) {
                w("bindToActiveNetwork failed: ${t.javaClass.simpleName} ${t.message}")
            }
        }
    }

    private fun tryToReuseSocket(): MulticastSocket? {
        d("tryToReuseSocket")
        return try {
            val srcAddress = InetSocketAddress(udpPort)
            val connection = MulticastSocket(null)
            connection.reuseAddress = true
            connection.trafficClass = IPTOS_RELIABILITY or IPTOS_THROUGHPUT or IPTOS_LOWDELAY
            connection.receiveBufferSize = RECEIVE_BUFFER_SIZE
            connection.bind(srcAddress)
            w(
                "MulticastSocket receiveBufferSize: " + connection.receiveBufferSize
                        + ", socket isBound = " + connection.isBound
                        + ", socket isClosed = " + connection.isClosed
                        + ", socket isConnected = " + connection.isConnected
            )
            connection
        } catch (ex: SocketException) {
            w("MulticastSocket can't be created, and can't be reused: " + ex.javaClass + " " + ex.localizedMessage)
            null
        } catch (e: UnknownHostException) {
            w("MulticastSocket can't be created, and can't be reuse UnknownHostException: " + e.localizedMessage)
            null
        } catch (e: IOException) {
            w("MulticastSocket can't be created, and can't be reuse IOException: " + e.localizedMessage)
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
