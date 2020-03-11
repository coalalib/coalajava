package com.ndmsystems.coala.functional;

import com.ndmsystems.coala.CoAPHandler;
import com.ndmsystems.coala.CoAPObservableResource;
import com.ndmsystems.coala.CoAPResource;
import com.ndmsystems.coala.CoAPResourceInput;
import com.ndmsystems.coala.CoAPResourceOutput;
import com.ndmsystems.coala.Coala;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessagePayload;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.infrastructure.logging.LogHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

/**
 * Created by bas on 16.11.16.
 */

public class ObserveTest extends BaseAsyncTest {
    private Coala client;
    private Coala server;

    @Before
    public void clear() {
        init();
    }

    @After
    public void stop() {
        client.stop();
        server.stop();
        client = null;
        server = null;

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testObserveSuccessSubscribe() {
        client = new Coala(4538);
        server = new Coala(5685);
        w(30);

        server.addObservableResource("msg", new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                onDataReceived(true);
                LogHelper.d("testObserve receive inputData");
                return new CoAPResourceOutput(new CoAPMessagePayload("ehu"), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain);
            }
        });

        client.start();
        server.start();

        client.registerObserver("coap://127.0.0.1:5685/msg").blockingFirst();
        waitAndExit(2000);
    }

    @Test
    public void testObserveSuccessGetNotification() {
        client = new Coala(1111);
        server = new Coala(2222);
        w(30);

        server.addObservableResource("msg", new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                LogHelper.d("testObserve receive inputData");
                return new CoAPResourceOutput(new CoAPMessagePayload("Hello!".getBytes()), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain);
            }
        });

        client.start();
        server.start();


        client.registerObserver("coap://127.0.0.1:2222/msg").subscribe(
                response -> onDataReceived(true),
                throwable -> onDataReceived(false)
        );
        waitAndExit(2000);
    }

    @Test
//    @Ignore("disable due error that required deep research")
    public void testObserveSuccessBigNotification() {
        client = new Coala(1111);
        server = new Coala(2222);
        w(30);

        final String bigText = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";

        server.addObservableResource("msg", new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                LogHelper.d("testObserve receive inputData");
                return new CoAPResourceOutput(new CoAPMessagePayload(bigText.getBytes()), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain);
            }
        });

        client.start();
        server.start();


        AtomicBoolean isNotificationReceived = new AtomicBoolean(false);

        w(30);

        client.registerObserver("coap://127.0.0.1:2222/msg").subscribe(
                response -> {
                    isNotificationReceived.set(true);
                },
                throwable -> {

                }
        );

        w(4000);

        assertTrue(isNotificationReceived.get());

        isNotificationReceived.set(false);

        CoAPObservableResource resource = server.getObservableResource("msg");
        resource.notifyObservers();

        w(4000);

        assertTrue(isNotificationReceived.get());
    }

    @Test
    public void testObserveUnknownToken() {
        client = new Coala(1111);
        server = new Coala(2222);
        w(30);

        server.addObservableResource("msg", new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                LogHelper.d("testObserve receive inputData");
                return new CoAPResourceOutput(new CoAPMessagePayload("Hello!".getBytes()), CoAPMessageCode.CoapCodeContent, CoAPMessage.MediaType.TextPlain);
            }
        });

        client.start();
        server.start();


        byte[] token = new byte[]{1, 2};

        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.CoapCodeContent);
        message.setURI("coap://127.0.0.1:2222/msg");
        message.setToken(token);
        LogHelper.v("Token: " + Hex.encodeHexString(token));
        message.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionObserve, 0));
        client.send(message, new CoAPHandler() {
            @Override
            public void onMessage(CoAPMessage message, String error) {
                if (message != null && message.getType() == CoAPMessageType.RST) onDataReceived(true);
                else onDataReceived(false);
            }

            @Override
            public void onAckError(String error) {
                onDataReceived(false);
            }
        });

        waitAndExit(2000);
    }
}
