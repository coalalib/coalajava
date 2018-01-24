package com.ndmsystems.coala.network;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

public class UDPConnection {
    private DatagramSocket connection;
    private boolean isClosed = false;

    public UDPConnection(Integer port) throws SocketException {
        this.connection = new DatagramSocket(null);
        this.connection.bind(new InetSocketAddress(port));
    }

    public byte[] read() throws IOException {
        byte[] input = new byte[4096];
        DatagramPacket udpPacket = new DatagramPacket(input, input.length);

        try {
            this.connection.receive(udpPacket);
        } catch (IOException e) {
            throw e;
        }

        return udpPacket.getData();
    }
}
