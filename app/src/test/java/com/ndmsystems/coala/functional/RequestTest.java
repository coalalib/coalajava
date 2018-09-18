package com.ndmsystems.coala.functional;

import com.ndmsystems.coala.CoAPResource;
import com.ndmsystems.coala.CoAPResourceInput;
import com.ndmsystems.coala.CoAPResourceOutput;
import com.ndmsystems.coala.Coala;
import com.ndmsystems.coala.layers.response.ResponseData;
import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.layers.response.ResponseHandler;
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
 * Created by Владимир on 23.08.2017.
 */

public class RequestTest {

    private CountDownLatch lock;

    @Test
    public void onReceiveResponse_responseHandlerShouldBeCalled() throws InterruptedException {
        LogHelper.addLogger(new SystemOutLogger(""));
        lock = new CountDownLatch(1);
        String expectedResponse = "response!";

        Coala client = new Coala(3333);
        client.start();
        Coala server = new Coala(2222);
        server.addResource("path", CoAPRequestMethod.GET, new CoAPResource.CoAPResourceHandler() {
            @Override
            public CoAPResourceOutput onReceive(CoAPResourceInput inputData) {
                CoAPMessagePayload payload = new CoAPMessagePayload("response!");
                return new CoAPResourceOutput(payload, CoAPMessageCode.CoapCodeContent, null);
            }
        });
        server.start();

        Thread.sleep(50);

        AtomicBoolean responseReceived = new AtomicBoolean(false);
        AtomicBoolean responseIsCorrect = new AtomicBoolean(false);
        CoAPMessage request = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        request.setURI("coap://127.0.0.1:2222/path");
        request.setResponseHandler(new ResponseHandler() {

            @Override
            public void onResponse(ResponseData responseData) {
                responseReceived.set(true);
                responseIsCorrect.set(expectedResponse.equals(responseData.getPayload()));
                lock.countDown();
            }

            @Override
            public void onError(Throwable error) {
                lock.countDown();
            }
        });
        client.send(request, null);

        lock.await(1, TimeUnit.SECONDS);

        assertTrue(responseReceived.get());
        assertTrue(responseIsCorrect.get());

        client.stop();
        server.stop();
    }

}
