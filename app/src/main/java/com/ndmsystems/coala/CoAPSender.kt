package com.ndmsystems.coala

import com.ndmsystems.coala.helpers.logging.LogHelper
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.i
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.layers.LogLayer.Companion.getStringToPrintSendingMessage
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.MulticastSocket

class CoAPSender(
    private val connectionProvider: ConnectionProvider,
    private val messagePool: CoAPMessagePool,
    private val layersStack: LayersStack
) {
    var isStarted = false
        private set
    private var sendingThread: SendingThread? = null
    private var connection: MulticastSocket? = null
    @Synchronized
    fun start() {
        v("CoAPSender start")
        if (connection == null) connectionProvider.waitForConnection()
            .subscribe( { newConnection: MulticastSocket? ->
                LogHelper.d("CoAPSender started, socket: $newConnection")
                connection = newConnection
                startSendingThread()
            }, {
                LogHelper.i("Can't start CoAPSender: $it")
            })
    }

    private fun startSendingThread() {
        if (!isStarted || sendingThread != null && sendingThread!!.state == Thread.State.TERMINATED) {
            v("SendingAsyncTask try to start")
            sendingThread = SendingThread()
            sendingThread!!.start()
        }
        isStarted = true
    }

    @Synchronized
    fun stop() {
        isStarted = false
        if (sendingThread != null) {
            sendingThread!!.interrupt()
            sendingThread = null
        }
        connection = null
    }

    private inner class SendingThread : Thread() {
        override fun run() {
            v("SendingAsyncTask start")
            var message: CoAPMessage?
            while (true) {
                // Check if we need to Quit!
                if (isInterrupted) {
                    i("SendingAsyncTask stopped")
                    break
                }
                message = messagePool.next()
                if (message != null) {
                    var layerResult: LayersStack.LayerResult
                    try {
                        // Check if we need to Quit!
                        // Double check before Writing to Socket!
                        if (isInterrupted) {
                            i("SendingAsyncTask stopped")
                            break
                        }
                        val destinationAddressReference = Reference(message.address)
                        //Этот мерзкий хак нужен для того чтобы сохранить исходный адрес назначения, не измененный слоями
                        message.address = destinationAddressReference.get()
                        if (message.address == null) {
                            e("Message address == null in SendingThread")
                        }
                        // Run Layers Chain
                        try {
                            layerResult = layersStack.onSend(message, destinationAddressReference)
                        } catch (e: LayersStack.InterruptedException) {
                            e.printStackTrace()
                            continue
                        }
                        val messageForSend = layerResult.message ?: message
                        if (destinationAddressReference.get() == null) {
                            e("Destination is null!! isNeedToSend = " + layerResult.shouldContinue + ", message = " + getStringToPrintSendingMessage(messageForSend, destinationAddressReference))
                        } else {
                            if (destinationAddressReference.get().toString().contains("local")) {
                                e("Try to send to localhost!!!")
                            }
                        }

                        // send it now!
                        if (layerResult.shouldContinue) {
                            if (destinationAddressReference.get() == null) {
                                e("Destination is null, but need to sending, message = " + getStringToPrintSendingMessage(messageForSend, destinationAddressReference))
                            } else {
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
                        if (isInterrupted) {
                            i("SendingAsyncTask stopped")
                            break
                        }
                        if (waitSomeSeconds()) continue else break
                    }
                } else {
                    // Empty queue? Wait for some milliseconds...
                    if (!waitSomeSeconds()) break
                }

                // Check if we need to Quit!
                if (isInterrupted) {
                    i("SendingAsyncTask stopped")
                    break
                }
            }
            v("SendingAsyncTask end")
            if (isStarted) {
                sendingThread = null
                isStarted = false
                try {
                    sleep(500)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                d("Try to start sending thread")
                this@CoAPSender.start()
            }
        }

        private fun waitSomeSeconds(): Boolean {
            try {
                sleep(50)
            } catch (e: InterruptedException) {
                i("SendingAsyncTask stopped by Thread Interrupted: " + e.message)
                return false
            }
            return true
        }
    }

    @Throws(IOException::class)
    private fun sendMessageToAddress(address: InetSocketAddress?, message: CoAPMessage) {
        val messageData = CoAPSerializer.toBytes(message)
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
    }
}