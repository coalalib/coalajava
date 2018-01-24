package com.ndmsystems.coala.functional;

import com.ndmsystems.coala.CoAPResource;
import com.ndmsystems.coala.CoAPResourceInput;
import com.ndmsystems.coala.CoAPResourceOutput;
import com.ndmsystems.coala.Coala;
import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessagePayload;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.message.CoAPRequestMethod;
import com.ndmsystems.infrastructure.logging.SystemOutLogger;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

/**
 * Created by Владимир on 21.08.2017.
 */

public class BigDataTest {

    private static final String bigData = "Вышла Саша в Шоссе и пососала сушку, выйду на холм куль поправлю, лавировали лавировали да не вылавировали. Вышла Саша в Шоссе и пососала сушку, выйду на холм куль поправлю, лавировали лавировали да не вылавировали. Вышла Саша в Шоссе и пососала сушку, выйду на холм куль поправлю, лавировали лавировали да не вылавировали. Вышла Саша в Шоссе и пососала сушку, выйду на холм куль поправлю, лавировали лавировали.";

    private CountDownLatch lock;

    @Test
    public void whenServerRespondsWithBigResponse_clientShouldReceiveSameData() throws InterruptedException {
        w(100);
        LogHelper.addLogger(new SystemOutLogger(""));
        lock = new CountDownLatch(1);
        Coala client = new Coala(3456);
        Coala server = new Coala(3457);

        AtomicBoolean responseReceived = new AtomicBoolean(false);
        AtomicBoolean responseDataIsCorrect = new AtomicBoolean(false);
        server.addResource("msg", CoAPRequestMethod.GET, new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                return new CoAPResourceOutput(new CoAPMessagePayload(bigData.getBytes()), CoAPMessageCode.CoapCodeContent, null);
            }
        });
        w(100);

        CoAPMessage request = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        request.setURI("coap://127.0.0.1:3457/msg");

        client.sendRequest(request).subscribe(
                response -> {
                    responseReceived.set(true);
                    if (bigData.equals(response.getPayload()))
                        responseDataIsCorrect.set(true);
                    lock.countDown();
                },
                ignore -> {
                }
        );


        lock.await(40, TimeUnit.SECONDS);
        assertTrue(responseReceived.get());
        assertTrue(responseDataIsCorrect.get());

        client.stop();
        server.stop();
    }

    @Test
    public void whenClientSendsBigRequest_serverShouldReceiveSameData() throws InterruptedException {
        LogHelper.addLogger(new SystemOutLogger(""));
        w(100);
        lock = new CountDownLatch(1);
        Coala client = new Coala(3456);
        Coala server = new Coala(3457);

        AtomicBoolean requestReceived = new AtomicBoolean(false);
        AtomicBoolean requestDataIsCorrect = new AtomicBoolean(false);
        server.addResource("msg", CoAPRequestMethod.POST, new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                try {
                    requestReceived.set(true);
                    CoAPMessage inputMessage = inputData.message;
                    CoAPMessagePayload messagePayload = inputMessage.getPayload();
                    String payloadText = messagePayload.toString();
                    requestDataIsCorrect.set(bigData.equals(payloadText));
                    return new CoAPResourceOutput(new CoAPMessagePayload("asd".getBytes()), CoAPMessageCode.CoapCodeContent, null);
                } finally {
                    lock.countDown();
                }
            }
        });
        w(100);

        CoAPMessage request = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST);
        request.setURI("coap://127.0.0.1:3457/msg");
        request.setPayload(new CoAPMessagePayload(bigData));

        client.send(request).subscribe(
                message -> {
                    LogHelper.v("message: " + message);
                },
                throwable -> {
                    LogHelper.v("throwable: " + throwable);
                }
        );


        lock.await(4, TimeUnit.SECONDS);
        assertTrue(requestReceived.get());
        assertTrue(requestDataIsCorrect.get());

        client.stop();
        server.stop();
    }

    @Test
    public void whenClientSendsBigRequest_andServerRespondsWithBigResponse_serverAndClientShouldReceiveCorrectData() throws InterruptedException {
        LogHelper.addLogger(new SystemOutLogger(""));
        w(100);
        lock = new CountDownLatch(2);
        Coala client = new Coala(3456);
        Coala server = new Coala(3457);

        AtomicBoolean requestReceived = new AtomicBoolean(false);
        AtomicBoolean requestDataIsCorrect = new AtomicBoolean(false);
        server.addResource("msg", CoAPRequestMethod.POST, new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                try {
                    requestReceived.set(true);
                    CoAPMessage inputMessage = inputData.message;
                    CoAPMessagePayload messagePayload = inputMessage.getPayload();
                    String payloadText = messagePayload.toString();
                    LogHelper.d("Server received request: " + payloadText);
                    requestDataIsCorrect.set(bigData.equals(payloadText));
                    return new CoAPResourceOutput(new CoAPMessagePayload(bigData.getBytes()), CoAPMessageCode.CoapCodeContent, null);
                } finally {
                    lock.countDown();
                }
            }
        });
        w(100);

        CoAPMessage request = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST);
        request.setURI("coap://127.0.0.1:3457/msg");
        request.setPayload(new CoAPMessagePayload(bigData));

        AtomicBoolean responseReceived = new AtomicBoolean(false);
        AtomicBoolean responseDataIsCorrect = new AtomicBoolean(false);
        client.sendRequest(request).subscribe(
                response -> {
                    LogHelper.d("Client received: " + response);
                    responseReceived.set(true);
                    if (bigData.equals(response.getPayload()))
                        responseDataIsCorrect.set(true);
                    lock.countDown();
                },
                throwable -> {
                }
        );


        lock.await(4, TimeUnit.SECONDS);
        assertTrue(requestReceived.get());
        assertTrue(requestDataIsCorrect.get());
        assertTrue(responseReceived.get());
        assertTrue(responseDataIsCorrect.get());

        client.stop();
        server.stop();
    }

    private void w(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException ignore) {
        }
    }
}
