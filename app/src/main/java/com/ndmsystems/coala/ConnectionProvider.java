package com.ndmsystems.coala;

import com.ndmsystems.infrastructure.logging.LogHelper;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

/**
 * Created by Владимир on 19.07.2017.
 */

public class ConnectionProvider {

    private Coala.OnPortIsBusyHandler onPortIsBusyHandler;
    private int port;
    private MulticastSocket connection;
    private Subject<MulticastSocket> subject = PublishSubject.create();
    private Disposable timerSubscription;

    public ConnectionProvider(int port) {
        this.port = port;;
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
            connection.disconnect();
            connection.close();
            connection = null;
        }

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
            DatagramSocket testSocket = new DatagramSocket(port);
            testSocket.setReuseAddress(true);
            testSocket.disconnect();
            testSocket.close();

            MulticastSocket connection = new MulticastSocket(port);
            connection.joinGroup(Inet4Address.getByName("224.0.0.187"));
            connection.setReceiveBufferSize(409600);
            connection.setLoopbackMode(true);
            LogHelper.w("MulticastSocket receiveBufferSize: " + connection.getReceiveBufferSize()
                    + ", socket isBound = " + connection.isBound()
                    + ", socket isClosed = " + connection.isClosed()
                    + ", socket isConnected = " + connection.isConnected());
            saveConnection(connection);
            return connection;
        } catch (SocketException ex) {
            LogHelper.e("MulticastSocket can't be created: " + ex.getLocalizedMessage());
            if (onPortIsBusyHandler != null) {
                onPortIsBusyHandler.onPortIsBusy();
            }
            return null;
        }
    }

    public void setOnPortIsBusyHandler(Coala.OnPortIsBusyHandler onPortIsBusyHandler) {
        this.onPortIsBusyHandler = onPortIsBusyHandler;
    }
}
