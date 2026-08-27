package com.ndmsystems.coala

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Opens the connection to the TCP proxy that [ConnectionProvider] hands out.
 *
 * Split out for the same reason as [UdpSocketFactory]: without it the proxy path cannot be reached
 * from a test at all, because every route into it starts with a real `connect()`.
 */
internal fun interface TcpSocketFactory {

    @Throws(IOException::class)
    fun connect(address: InetSocketAddress, timeoutMillis: Int): Socket
}

internal class RealTcpSocketFactory : TcpSocketFactory {

    @Throws(IOException::class)
    override fun connect(address: InetSocketAddress, timeoutMillis: Int): Socket =
        Socket().apply { connect(address, timeoutMillis) }
}
