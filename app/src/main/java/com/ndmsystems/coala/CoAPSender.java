package com.ndmsystems.coala;

import com.ndmsystems.coala.layers.LogLayer;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.utils.Reference;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;

public class CoAPSender {

    private Boolean isRunning = false;
    private SendingThread sendingThread = null;
    private LayersStack layersStack;
    private MulticastSocket connection;
    private CoAPMessagePool messagePool;
    private ConnectionProvider connectionProvider;

    public CoAPSender(ConnectionProvider connectionProvider,
                      CoAPMessagePool messagePool,
                      LayersStack sendLayerStack) {
        this.connectionProvider = connectionProvider;
        this.messagePool = messagePool;
        this.layersStack = sendLayerStack;
    }

    public synchronized void start() {
        if (connection == null)
            connectionProvider.waitForConnection()
                    .subscribe(
                            newConnection -> {
                                connection = newConnection;
                                startSendingThread();
                            }
                    );
    }

    private void startSendingThread() {
        if (!isRunning ||
                (sendingThread != null && sendingThread.getState() == Thread.State.TERMINATED)) {
            LogHelper.v("SendingAsyncTask try to start");
            sendingThread = new SendingThread();
            sendingThread.start();
        }

        isRunning = true;
    }

    public synchronized void stop() {
        isRunning = false;
        if (sendingThread != null) {
            sendingThread.interrupt();
            sendingThread = null;
        }

        connection = null;
    }

    public boolean isStarted() {
        return isRunning;
    }

    private class SendingThread extends Thread {

        @Override
        public void run() {
            LogHelper.v("SendingAsyncTask start");
            CoAPMessage message;

            while (true) {
                // Check if we need to Quit!
                if (isInterrupted()) {
                    LogHelper.i("SendingAsyncTask stopped");
                    break;
                }

                message = messagePool.next();

                if (message != null) {
                    try {
                        // Check if we need to Quit!
                        // Double check before Writing to Socket!
                        if (isInterrupted()) {
                            LogHelper.i("SendingAsyncTask stopped");
                            break;
                        }

                        Reference<InetSocketAddress> destinationAddressReference = new Reference<>(message.getAddress());
                        //Этот мерзкий хак нужен для того чтобы сохранить исходный адрес назначения, не измененный слоями
                        message.setAddress(destinationAddressReference.get());
                        if (message.getAddress() == null) {
                            LogHelper.e("Message address == null in SendingThread");
                        }

                        boolean isNeedToSend;
                        // Run Layers Chain
                        try {
                            isNeedToSend = layersStack.onSend(message, destinationAddressReference);
                        } catch (LayersStack.InterruptedException e) {
                            e.printStackTrace();
                            continue;
                        }
                        if (destinationAddressReference.get() == null) {
                            LogHelper.e("Destination is null!! isNeedToSend = " + isNeedToSend + ", message = " + LogLayer.getStringToPrintSendingMessage(message, null));
                        } else {
                            if (destinationAddressReference.get().toString().contains("local")) {
                                LogHelper.e("Try to send to localhost!!!");
                            }
                        }

                        // send it now!
                        if (isNeedToSend) {
                            if (destinationAddressReference.get() == null) {
                                LogHelper.e("Destination is null, but need to sending, message = " + LogLayer.getStringToPrintSendingMessage(message, null));
                            } else {
                                sendMessageToAddress(destinationAddressReference.get(), message);
                            }
                        }
                        
                    } catch (IOException e) {
                        LogHelper.d("IOException: " + e.getMessage());
                        if (isInterrupted()) {
                            LogHelper.i("SendingAsyncTask stopped");
                            break;
                        }
                        if (waitSomeSeconds()) continue;
                        else break;
                    }

                    // post-process
                    if (message.getType() != CoAPMessageType.CON) {
                        // we can remove this message from Pool right away if it's not CON
                        messagePool.remove(message);
                    }

                } else {
                    // Empty queue? Wait for some milliseconds...
                    if (!waitSomeSeconds()) break;
                }

                // Check if we need to Quit!
                if (isInterrupted()) {
                    LogHelper.i("SendingAsyncTask stopped");
                    break;
                }
            }
            LogHelper.v("SendingAsyncTask end");

            if (isRunning) {
                sendingThread = null;
                isRunning = false;

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                LogHelper.d("Try to start sending thread");

                CoAPSender.this.start();
            }
        }

        private boolean waitSomeSeconds() {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                LogHelper.i("SendingAsyncTask stopped by Thread Interrupted: " + e.getMessage());
                return false;
            }
            return true;
        }
    }

    private void sendMessageToAddress(InetSocketAddress address, CoAPMessage message) throws IOException {
        byte[] messageData = CoAPSerializer.toBytes(message);
        DatagramPacket udpPacket = new DatagramPacket(messageData, messageData.length, address);

        // Send data!
        if (connection != null) {
            connection.send(udpPacket);
        }
    }
}
