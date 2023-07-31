package com.ndmsystems.coala

import com.ndmsystems.coala.Coala.OnPortIsBusyHandler
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.i
import com.ndmsystems.coala.helpers.logging.LogHelper.w
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Created by Владимир on 19.07.2017.
 */
class ConnectionProvider(private val port: Int) {
    private var onPortIsBusyHandler: OnPortIsBusyHandler? = null
    private var connection: MulticastSocket? = null
    private val subject: Subject<MulticastSocket> = PublishSubject.create()
    private var timerSubscription: Disposable? = null
    @Synchronized
    fun waitForConnection(): Observable<MulticastSocket> {
        return if (connection != null) Observable.just(connection) else {
            initConnection()
            subject
        }
    }

    @Synchronized
    fun close() {
        if (connection != null &&
            !connection!!.isClosed
        ) {
            connection!!.close()
        }
        connection = null
        if (timerSubscription != null &&
            !timerSubscription!!.isDisposed
        ) {
            timerSubscription!!.dispose()
            timerSubscription = null
        }
    }

    @Synchronized
    private fun initConnection() {
        if (timerSubscription == null) timerSubscription = Observable.interval(10, 1000, TimeUnit.MILLISECONDS)
            .map { createConnection() }
            .filter { connection: MulticastSocket? -> connection != null }
            .retry()
            .subscribe()
    }

    @Synchronized
    private fun saveConnection(connection: MulticastSocket) {
        this.connection = connection
        subject.onNext(connection)
        timerSubscription!!.dispose()
        timerSubscription = null
    }

    @Synchronized
    @Throws(IOException::class)
    private fun createConnection(): MulticastSocket? {
        return try {
            val connection = MulticastSocket(port)
            connection.joinGroup(Inet4Address.getByName("224.0.0.187"))
            connection.receiveBufferSize = 409600
            connection.trafficClass = IPTOS_RELIABILITY or IPTOS_THROUGHPUT or IPTOS_LOWDELAY
            saveConnection(connection)
            connection
        } catch (ex: SocketException) {
            i("MulticastSocket can't be created, try to reuse: " + ex.javaClass + " " + ex.localizedMessage)
            tryToReuseSocket()
        }
    }

    private fun tryToReuseSocket(): MulticastSocket? {
        return try {
            val srcAddress = InetSocketAddress(port)
            val connection = MulticastSocket(null)
            connection.reuseAddress = true
            connection.trafficClass = IPTOS_RELIABILITY or IPTOS_THROUGHPUT or IPTOS_LOWDELAY
            connection.joinGroup(Inet4Address.getByName("224.0.0.187"))
            connection.receiveBufferSize = 1048576
            connection.bind(srcAddress)
            w(
                "MulticastSocket receiveBufferSize: " + connection.receiveBufferSize
                        + ", socket isBound = " + connection.isBound
                        + ", socket isClosed = " + connection.isClosed
                        + ", socket isConnected = " + connection.isConnected
            )
            saveConnection(connection)
            connection
        } catch (ex: SocketException) {
            e("MulticastSocket can't be created, and can't be reused: " + ex.javaClass + " " + ex.localizedMessage)
            if (onPortIsBusyHandler != null) {
                onPortIsBusyHandler!!.onPortIsBusy()
            }
            null
        } catch (e: UnknownHostException) {
            e("MulticastSocket can't be created, and can't be reuse UnknownHostException: " + e.localizedMessage)
            e.printStackTrace()
            if (onPortIsBusyHandler != null) {
                onPortIsBusyHandler!!.onPortIsBusy()
            }
            null
        } catch (e: IOException) {
            e("MulticastSocket can't be created, and can't be reuse IOException: " + e.localizedMessage)
            e.printStackTrace()
            if (onPortIsBusyHandler != null) {
                onPortIsBusyHandler!!.onPortIsBusy()
            }
            null
        }
    }

    fun setOnPortIsBusyHandler(onPortIsBusyHandler: OnPortIsBusyHandler?) {
        d("setOnPortIsBusyHandler")
        this.onPortIsBusyHandler = onPortIsBusyHandler
    }

    fun restartConnection() {
        if (connection != null && !connection!!.isClosed) {
            connection!!.close()
        }
        connection = null
    }

    companion object {
        private const val IPTOS_RELIABILITY = 0x04
        private const val IPTOS_THROUGHPUT = 0x08
        private const val IPTOS_LOWDELAY = 0x10
    }
}