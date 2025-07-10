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

class CoAPReceiver(
    private val connectionProvider: ConnectionProvider,
    private val receiveLayerStack: LayersStack
) {
    var isStarted = false
        private set
    private var receivingThread: ReceivingThread? = null
    private var connection: MulticastSocket? = null
    private var tcpReceivingThread: Thread? = null
    private var transportMode: Coala.TransportMode = Coala.TransportMode.UDP

    @Synchronized
    fun start() {
        v("CoAPReceiver start")
        if (transportMode == Coala.TransportMode.UDP) {
            if (connection == null) connectionProvider.waitForUdpConnection()
                .subscribe( { newConnection: MulticastSocket? ->
                    v("CoAPReceiver started, socket: $newConnection")
                    connection = newConnection
                    startReceivingThread()
                }, {
                    e("Can't start CoAPReceiver: $it")
                })
            else {
                startReceivingThread()
            }
        } else if (transportMode == Coala.TransportMode.TCP) {
            i("CoAPReceiver TCP mode start")
            startTcpReceivingThread()
        }
    }

    private fun startReceivingThread() {
        if (!isStarted || receivingThread != null && receivingThread!!.state == Thread.State.TERMINATED) {
            v("ReceivingAsyncTask try to start, state is ${receivingThread?.state}")
            receivingThread = ReceivingThread()
            receivingThread!!.start()
        }
        isStarted = true
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
                    d("IOException when try to receive message: ${e.message}")
                    continue
                }
                if (isInterrupted || !isStarted) {
                    d("isInterrupted() = $isInterrupted isRunning = $isStarted stopping")
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
                    val senderAddressReference = Reference(socketAddress)
                    message.address = senderAddressReference.get()
                    if (message.address == null) {
                        e("Message address == null in ReceivingThread")
                    }
                    receiveLayerStack.onReceive(message, senderAddressReference)
                } catch (e: LayersStack.InterruptedException) {
                    e.printStackTrace()
                } catch (ex: Exception) {
                    i("Exception: ${ex.message}")
                    ex.printStackTrace()
                    continue
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

    private fun getMessageFromPacket(udpPacket: DatagramPacket, addressFrom: InetSocketAddress? = null): CoAPMessage? {
        val data = ByteArray(udpPacket.length)
        System.arraycopy(udpPacket.data, udpPacket.offset, data, 0, udpPacket.length)
        val message: CoAPMessage? = try {
            fromBytes(data, addressFrom)
        } catch (e: DeserializeException) {
            e("Deserialization error: " + e.message)
            if (BuildConfig.DEBUG) e.printStackTrace()
            return null
        }
        return message
    }

    private fun startTcpReceivingThread() {
        if (tcpReceivingThread == null || tcpReceivingThread?.state == Thread.State.TERMINATED) {
            tcpReceivingThread = Thread {
                try {
                    val socket = connectionProvider.getOrCreateTcpSocket()
                    val input = socket.getInputStream()
                    while (!Thread.currentThread().isInterrupted && isStarted) {
                        // Читаем фрейм: M (1B) | IP (4B) | PORT (2B) | SIZE (2B) | MESSAGE (SIZE B)
                        val header = ByteArray(9)
                        var read = 0
                        while (read < 9) {
                            val r = input.read(header, read, 9 - read)
                            if (r == -1) throw java.io.EOFException()
                            read += r
                        }
                        if (header[0] != 77.toByte()) continue // не наш фрейм
                        val ip = java.net.InetAddress.getByAddress(header.sliceArray(1..4))
                        val port = ((header[5].toInt() and 0xFF) shl 8) or (header[6].toInt() and 0xFF)
                        val size = ((header[7].toInt() and 0xFF) shl 8) or (header[8].toInt() and 0xFF)
                        val messageBytes = ByteArray(size)
                        var mRead = 0
                        while (mRead < size) {
                            val r = input.read(messageBytes, mRead, size - mRead)
                            if (r == -1) throw java.io.EOFException()
                            mRead += r
                        }
                        val address = InetSocketAddress(ip, port)
                        val message = try {
                            fromBytes(messageBytes, address)
                        } catch (e: Exception) {
                            e("TCP frame parse error: ${e.message}")
                            null
                        }
                        d("Received from tcp socket $message")
                        if (message != null) {
                            val senderAddressReference = Reference(address)
                            message.address = senderAddressReference.get()
                            receiveLayerStack.onReceive(message, senderAddressReference)
                        }
                    }
                } catch (e: LayersStack.InterruptedException) {
                    d("TCP receiving thread interrupted")
                    e.printStackTrace()
                } catch (e: Exception) {
                    LogHelper.e("TCP receiving thread error: ${e.message}")
                    e.printStackTrace()
                }
            }
            isStarted = true
            tcpReceivingThread!!.start()
        }
    }

    fun setTransportMode(mode: Coala.TransportMode) {
        if (transportMode == mode) return
        stop()

        transportMode = mode
        // Если был запущен — запускаем снова
        start()
    }

    companion object {
        const val TAG = "CoAPReceiver"
    }
}