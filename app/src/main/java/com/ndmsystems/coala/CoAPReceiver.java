package com.ndmsystems.coala;

import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.utils.Reference;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;

import io.reactivex.disposables.Disposable;

public class CoAPReceiver {

    private final ConnectionProvider connectionProvider;
    private final CoAPClient client;
    private Boolean isRunning = false;
    private ReceivingThread receivingThread = null;
    private MulticastSocket connection;
    private Disposable connectionSubscription;

    private LayersStack receiveLayerStack;

    public CoAPReceiver(ConnectionProvider connectionProvider,
                        CoAPClient client,
                        LayersStack receiveLayerStack) {
        this.connectionProvider = connectionProvider;
        this.client = client;
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
        if (connectionSubscription != null &&
                !connectionSubscription.isDisposed()) {
            connectionSubscription.dispose();
            connectionSubscription = null;
            connection = null;
        }
    }

    private class ReceivingThread extends Thread {

        @Override
        public void run() {
            LogHelper.v("ReceivingAsyncTask start");

            while (true) {
                // check if Cancelled
                if (isInterrupted() || !isRunning) {
                    LogHelper.i("ReceivingAsyncTask stopped");
                    break;
                }

                // prepare udp packer
                byte[] input = new byte[4096];
                DatagramPacket udpPacket = new DatagramPacket(input, input.length);

                // Reading from UDP
                try {
                    connection.receive(udpPacket);
                } catch (IOException e) {
                    LogHelper.d("isInterrupted() = " + isInterrupted() + " isRunning = " + isRunning);
                    e.printStackTrace();
                    if (isInterrupted() || !isRunning) {
                        LogHelper.i("ReceivingAsyncTask stopped");
                        break;
                    }
                }

                // Build message from bytes
                CoAPMessage message = getMessageFromPacket(udpPacket);

                if (message == null) continue;
                if (message.getId() < 0) {
                    LogHelper.e("CoAPReceiver: Receiving data from CoAP Peer: Invalid Data. Skipping.");
                    continue;
                }

                //String block1Options = (message.getOption(Coala.OptionCode.OptionBlock1) != null ? " Block1: " + Block.fromInt((Integer) message.getOption(Coala.OptionCode.OptionBlock1).value).getPayload() : "");
                //String block2Options = (message.getOption(Coala.OptionCode.OptionBlock2) != null ? " Block2: " + Block.fromInt((Integer) message.getOption(Coala.OptionCode.OptionBlock2).value).getPayload() : "");

                // Run Layers Chain
                try {
                    Reference<InetSocketAddress> senderAddressReference = new Reference<>((InetSocketAddress) udpPacket.getSocketAddress());
                    message.setDestination(senderAddressReference.get());
                    receiveLayerStack.onReceive(message, senderAddressReference);
                } catch (LayersStack.InterruptedException e) {
                    e.printStackTrace();
                    continue;
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
            e.printStackTrace();

            if (e.getMessageId() != null) {//TODO: вероятно стоит перенести в другое место
                CoAPMessage resetMessage = new CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.CoapCodeEmpty, e.getMessageId());
                resetMessage.setStringPayload(e.getMessage());

                resetMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIHost, udpPacket.getAddress().getHostAddress()));
                resetMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIPort, udpPacket.getPort()));

                client.send(resetMessage, null);
            }
            return null;
        }
        return message;
    }
}
