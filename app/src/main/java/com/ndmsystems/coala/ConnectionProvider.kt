package com.ndmsystems.coala

import com.ndmsystems.coala.Coala.OnPortIsBusyHandler
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.i
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.helpers.logging.LogHelper.w
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.AsyncSubject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketException
import java.net.UnknownHostException

/**
 * Created by Владимир on 19.07.2017.
 */
class ConnectionProvider(private val port: Int) {
    private var onPortIsBusyHandler: OnPortIsBusyHandler? = null
    private var connection: MulticastSocket? = null
    private var subject: AsyncSubject<MulticastSocket>? = null
    private var timerSubscription: Disposable? = null

    @Synchronized
    fun waitForConnection(): Observable<MulticastSocket> {
        v("waitForConnection")
        return if (connection != null) {
            v("waitForConnection return connection")
            Observable.just(connection)
        } else {
            if (subject == null) {
                v("waitForConnection initConnection")
                subject = AsyncSubject.create()
                initConnection()
            }
            v("waitForConnection return subject")
            subject!!
        }
    }

    @Synchronized
    fun close() {
        d("close")
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
        subject?.onError(Exception("Closed"))
        subject = null
    }

    private fun initConnection() {
        if (timerSubscription == null) timerSubscription = Observable.just(0)
            .map {
                val newConnection = createConnection() ?: throw Exception("Can't create connection")
                newConnection
            }
            .map {
                saveConnection(it)
                invokeResultAndCompleteSubject(it)
                it
            }
            .retry(3)
            .doOnError {
            }
            .subscribeOn(Schedulers.io())
            .subscribe({}, {
                i("Can't init connection: ${it.message}")
                timerSubscription?.dispose()
                timerSubscription = null
                subject?.onError(it)
                setSubjectToNull()
                invokePortIsBusyIfNeeded()
            })
    }

    private fun invokeResultAndCompleteSubject(connection: MulticastSocket) {
        subject?.onNext(connection)
        subject?.onComplete()

        setSubjectToNull()
    }

    @Synchronized
    private fun setSubjectToNull() {
        subject = null
    }

    @Throws(IOException::class)
    private fun createConnection(): MulticastSocket? {
        return try {
            val connection = MulticastSocket(port)
            connection.joinGroup(Inet4Address.getByName("224.0.0.187"))
            connection.receiveBufferSize = 1048576
            connection.trafficClass = IPTOS_RELIABILITY or IPTOS_THROUGHPUT or IPTOS_LOWDELAY
            connection
        } catch (ex: SocketException) {
            i("MulticastSocket can't be created, try to reuse: " + ex.javaClass + " " + ex.localizedMessage)
            tryToReuseSocket()
        }
    }

    @Synchronized
    private fun saveConnection(connection: MulticastSocket) {
        d("saveConnection")
        this.connection = connection
        timerSubscription?.dispose()
        timerSubscription = null
    }

    private fun tryToReuseSocket(): MulticastSocket? {
        d("tryToReuseSocket")
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
            invokeResultAndCompleteSubject(connection)
            connection
        } catch (ex: SocketException) {
            i("MulticastSocket can't be created, and can't be reused: " + ex.javaClass + " " + ex.localizedMessage)
            null
        } catch (e: UnknownHostException) {
            i("MulticastSocket can't be created, and can't be reuse UnknownHostException: " + e.localizedMessage)
            e.printStackTrace()
            null
        } catch (e: IOException) {
            i("MulticastSocket can't be created, and can't be reuse IOException: " + e.localizedMessage)
            e.printStackTrace()
            null
        }
    }

    private fun invokePortIsBusyIfNeeded() {
        if (onPortIsBusyHandler != null) {
            onPortIsBusyHandler!!.onPortIsBusy()
        }
    }

    fun setOnPortIsBusyHandler(onPortIsBusyHandler: OnPortIsBusyHandler?) {
        d("setOnPortIsBusyHandler")
        this.onPortIsBusyHandler = onPortIsBusyHandler
    }

    companion object {
        private const val IPTOS_RELIABILITY = 0x04
        private const val IPTOS_THROUGHPUT = 0x08
        private const val IPTOS_LOWDELAY = 0x10
    }
}