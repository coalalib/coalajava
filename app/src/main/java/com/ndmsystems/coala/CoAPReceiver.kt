package com.ndmsystems.coala

import com.ndmsystems.coala.CoAPSerializer.DeserializeException
import com.ndmsystems.coala.CoAPSerializer.fromBytes
import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.i
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.layers.arq.states.LoggableState
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.utils.Reference
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.MulticastSocket

class CoAPReceiver(private val connectionProvider: ConnectionProvider, private val receiveLayerStack: LayersStack) {
    var isStarted = false
        private set
    private var receivingThread: ReceivingThread? = null
    private var connection: MulticastSocket? = null
    @Synchronized
    fun start() {
        v("CoAPReceiver start")
        if (connection == null) connectionProvider.waitForConnection()
            .subscribe( { newConnection: MulticastSocket? ->
                v("CoAPReceiver started, socket: $newConnection")
                connection = newConnection
                startReceivingThread()
            }, {
            LogHelper.i("Can't start CoAPReceiver: $it")
        })
    }

    @Synchronized
    private fun startReceivingThread() {
        if (!isStarted || receivingThread != null && receivingThread!!.state == Thread.State.TERMINATED) {
            v("ReceivingAsyncTask try to start")
            receivingThread = ReceivingThread()
            receivingThread!!.start()
            isStarted = true
        }
    }

    @Synchronized
    fun stop() {
        i("CoAPReceiver stop")
        isStarted = false
        if (receivingThread != null) {
            receivingThread!!.interrupt()
            receivingThread = null
        }
        connection = null
    }

    fun getReceivedStateForToken(token: ByteArray?): LoggableState? {
        return receiveLayerStack.getArqReceivedStateForToken(token!!)
    }

    //это не окончательный вариант, но NPE баг закрывает
    private inner class ReceivingThread : Thread() {
        override fun run() {
            v("ReceivingAsyncTask start")
            while (!isInterrupted && isStarted) {

                // prepare udp packer
                val input = ByteArray(4096)
                val udpPacket = DatagramPacket(input, input.size)

                // Reading from UDP
                try {
                    if (connection != null && !connection!!.isClosed) connection!!.receive(udpPacket) else {
                        interrupt()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                if (isInterrupted || !isStarted) {
                    d("isInterrupted() = $isInterrupted isRunning = $isStarted stopping")
                    break
                }

                // Build message from bytes
                val message = getMessageFromPacket(udpPacket) ?: continue
                if (message.id < 0) {
                    e("CoAPReceiver: Receiving data from CoAP Peer: Invalid Data. Skipping.")
                    continue
                }

                // Run Layers Chain
                try {
                    val senderAddressReference = Reference(udpPacket.socketAddress as InetSocketAddress)
                    message.address = senderAddressReference.get()
                    if (message.address == null) {
                        e("Message address == null in ReceivingThread")
                    }
                    receiveLayerStack.onReceive(message, senderAddressReference)
                } catch (e: LayersStack.InterruptedException) {
                    e.printStackTrace()
                }
            }
            v("ReceivingAsyncTask end")
            if (isStarted) {
                receivingThread = null
                isStarted = false
                try {
                    sleep(500)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                d("Try to start receiving thread")
                this@CoAPReceiver.start()
            }
        }
    }

    private fun getMessageFromPacket(udpPacket: DatagramPacket): CoAPMessage? {
        val data = ByteArray(udpPacket.length)
        System.arraycopy(udpPacket.data, udpPacket.offset, data, 0, udpPacket.length)
        val message: CoAPMessage? = try {
            fromBytes(data)
        } catch (e: DeserializeException) {
            e("Deserialization error: " + e.message)
            if (BuildConfig.DEBUG) e.printStackTrace()
            return null
        }
        return message
    }

    companion object {
        const val TAG = "CoAPReceiver"
    }
}