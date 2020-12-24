package com.ndmsystems.coala;

import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.utils.Reference;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;

public class CoAPReceiver {

    public static final String TAG = "CoAPReceiver";

    private final ConnectionProvider connectionProvider;
    private final LayersStack receiveLayerStack;

    private Boolean isRunning = false;
    private ReceivingThread receivingThread = null;
    private MulticastSocket connection;


    public CoAPReceiver(ConnectionProvider connectionProvider, LayersStack receiveLayerStack) {
        this.connectionProvider = connectionProvider;
        this.receiveLayerStack = receiveLayerStack;
    }


    public boolean isStarted() {
        return isRunning;
    }

    public synchronized void start() {
        if (connection == null)
            connectionProvider.waitForConnection()
                    .subscribe(
                            newConnection -> {
                                connection = newConnection;
                                startReceivingThread();
                            }
                    );
    }

    private synchronized void startReceivingThread() {
        if (!isRunning || (receivingThread != null && receivingThread.getState() == Thread.State.TERMINATED)) {
            LogHelper.v("ReceivingAsyncTask try to start");
            receivingThread = new ReceivingThread();
            receivingThread.start();
            isRunning = true;
        }
    }

    public synchronized void stop() {
        LogHelper.i("CoAPReceiver stop");
        isRunning = false;
        if (receivingThread != null) {
            receivingThread.interrupt();
            receivingThread = null;
        }

        connection = null;
    }

    //это не окончательный вариант, но NPE баг закрывает
    private class ReceivingThread extends Thread {

        @Override
        public void run() {
            LogHelper.v("ReceivingAsyncTask start");

            while (!isInterrupted() && isRunning) {

                // prepare udp packer
                byte[] input = new byte[4096];
                DatagramPacket udpPacket = new DatagramPacket(input, input.length);

                // Reading from UDP
                try {
                    if (connection != null && !connection.isClosed())
                        connection.receive(udpPacket);
                    else {
                        interrupt();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (isInterrupted() || !isRunning) {
                    LogHelper.d("isInterrupted() = " + isInterrupted() + " isRunning = " + isRunning + " stopping");
                    break;
                }

                // Build message from bytes
                CoAPMessage message = getMessageFromPacket(udpPacket);

                if (message == null) continue;
                if (message.getId() < 0) {
                    LogHelper.e("CoAPReceiver: Receiving data from CoAP Peer: Invalid Data. Skipping.");
                    continue;
                }

                // Run Layers Chain
                try {
                    Reference<InetSocketAddress> senderAddressReference = new Reference<>((InetSocketAddress) udpPacket.getSocketAddress());
                    message.setAddress(senderAddressReference.get());
                    if (message.getAddress() == null) {
                        LogHelper.e("Message address == null in ReceivingThread");
                    }
                    receiveLayerStack.onReceive(message, senderAddressReference);
                } catch (LayersStack.InterruptedException e) {
                    e.printStackTrace();
                }
            }

            LogHelper.v("ReceivingAsyncTask end");

            if (isRunning) {
                receivingThread = null;
                isRunning = false;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                LogHelper.d("Try to start receiving thread");

                CoAPReceiver.this.start();
            }
        }
    }

    private CoAPMessage getMessageFromPacket(DatagramPacket udpPacket) {
        byte[] data = new byte[udpPacket.getLength()];
        System.arraycopy(udpPacket.getData(), udpPacket.getOffset(), data, 0, udpPacket.getLength());

        CoAPMessage message;
        try {
            message = CoAPSerializer.fromBytes(data);
        } catch (CoAPSerializer.DeserializeException e) {
            LogHelper.e("Deserialization error: " + e.getMessage());
            if (BuildConfig.DEBUG) e.printStackTrace();
            return null;
        }
        return message;
    }
}
