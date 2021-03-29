package com.ndmsystems.coala;

import com.ndmsystems.infrastructure.logging.LogHelper;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

/**
 * Created by Владимир on 19.07.2017.
 */

public class ConnectionProvider {

    private static final int IPTOS_RELIABILITY = 0x04;
    private static final int IPTOS_THROUGHPUT = 0x08;
    private static final int IPTOS_LOWDELAY = 0x10;

    private Coala.OnPortIsBusyHandler onPortIsBusyHandler;
    private int port;
    private MulticastSocket connection;
    private Subject<MulticastSocket> subject = PublishSubject.create();
    private Disposable timerSubscription;

    public ConnectionProvider(int port) {
        this.port = port;
    }

    public synchronized Observable<MulticastSocket> waitForConnection() {
        if (connection != null)
            return Observable.just(connection);
        else {
            initConnection();
            return subject;
        }
    }

    public synchronized void close() {
        if (connection != null &&
                !connection.isClosed()) {
            connection.close();
        }
        connection = null;

        if (timerSubscription != null &&
                !timerSubscription.isDisposed()) {
            timerSubscription.dispose();
            timerSubscription = null;
        }
    }

    private synchronized void initConnection() {
        if (timerSubscription == null)
            timerSubscription = Observable.interval(10, 1000, TimeUnit.MILLISECONDS)
                    .map(ignore -> createConnection())
                    .filter(connection -> {
                        return connection != null;
                    })
                    .retry()
                    .subscribe();
    }

    private synchronized void saveConnection(MulticastSocket connection) {
        this.connection = connection;
        subject.onNext(connection);
        timerSubscription.dispose();
        timerSubscription = null;
    }

    private synchronized MulticastSocket createConnection() throws IOException {
        try {
            MulticastSocket connection = new MulticastSocket(port);
            connection.joinGroup(Inet4Address.getByName("224.0.0.187"));
            connection.setReceiveBufferSize(409600);
            connection.setTrafficClass(IPTOS_RELIABILITY | IPTOS_THROUGHPUT | IPTOS_LOWDELAY);
            saveConnection(connection);
            return connection;
        } catch (SocketException ex) {
            LogHelper.e("MulticastSocket can't be created, try to reuse: " + ex.getClass() + " " + ex.getLocalizedMessage());
            return tryToReuseSocket();
        }
    }

    private MulticastSocket tryToReuseSocket() {
        try {
            InetSocketAddress srcAddress = new InetSocketAddress(port);

            MulticastSocket connection = new MulticastSocket(null);
            connection.setReuseAddress(true);
            connection.setTrafficClass(IPTOS_RELIABILITY | IPTOS_THROUGHPUT | IPTOS_LOWDELAY);
            connection.joinGroup(Inet4Address.getByName("224.0.0.187"));
            connection.setReceiveBufferSize(409600);
            connection.bind(srcAddress);

            LogHelper.w("MulticastSocket receiveBufferSize: " + connection.getReceiveBufferSize()
                    + ", socket isBound = " + connection.isBound()
                    + ", socket isClosed = " + connection.isClosed()
                    + ", socket isConnected = " + connection.isConnected());
            saveConnection(connection);
            return connection;
        } catch (SocketException ex) {
            LogHelper.e("MulticastSocket can't be created, and can't be reused: " + ex.getClass() + " " + ex.getLocalizedMessage());

            if (onPortIsBusyHandler != null) {
                onPortIsBusyHandler.onPortIsBusy();
            }
            return null;
        } catch (UnknownHostException e) {
            LogHelper.e("MulticastSocket can't be created, and can't be reuse UnknownHostException: " + e.getLocalizedMessage());
            e.printStackTrace();
            if (onPortIsBusyHandler != null) {
                onPortIsBusyHandler.onPortIsBusy();
            }
            return null;
        } catch (IOException e) {
            LogHelper.e("MulticastSocket can't be created, and can't be reuse IOException: " + e.getLocalizedMessage());
            e.printStackTrace();
            if (onPortIsBusyHandler != null) {
                onPortIsBusyHandler.onPortIsBusy();
            }
            return null;
        }
    }

    public void setOnPortIsBusyHandler(Coala.OnPortIsBusyHandler onPortIsBusyHandler) {
        LogHelper.d("setOnPortIsBusyHandler");
        this.onPortIsBusyHandler = onPortIsBusyHandler;
    }

    public void restartConnection() {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }

        connection = null;

    }
}
