package com.ndmsystems.coala.layers;

import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPHandler;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.coala.layers.ProxyLayer;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.utils.Reference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Created by Владимир on 01.06.2017.
 */
public class ProxyLayerTest {
    private ProxyLayer proxyLayer;
    private CoAPClient client;
    private CoAPMessagePool messagePool;

    @Before
    public void setUp() throws Exception {
        client = mock(CoAPClient.class);
        messagePool = mock(CoAPMessagePool.class);
        proxyLayer = new ProxyLayer(client, messagePool);
    }

    @Test
    public void onSend_notAProxyMessage_shouldSkip() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET);
        InetSocketAddress proxyAddress = new InetSocketAddress("123.123.123.123", 12345);
        Reference<InetSocketAddress> addressReference = new Reference<>(proxyAddress);

        boolean shouldContinue = proxyLayer.onSend(message, addressReference);

        assertTrue(shouldContinue);
        verify(client, times(0)).send((CoAPMessage) any(), (CoAPHandler) any());
    }

    @Test
    public void onSend_messageThroughProxy_shouldRemoveRestrictedOptions(){
        CoAPMessage message = new CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET);
        message.setURI("coap://123.123.123.124:5556/asd?asd=asd");
        InetSocketAddress destinationAddress = message.getDestination();
        InetSocketAddress proxyAddress = new InetSocketAddress("123.123.123.123", 5432);
        Reference<InetSocketAddress> addressReference = new Reference<>(proxyAddress);
        message.setProxy(proxyAddress);

        boolean shouldContinue = proxyLayer.onSend(message, addressReference);

        assertTrue(shouldContinue);
        /*assertNull(message.getOption(CoAPMessageOptionCode.OptionURIHost));
        assertNull(message.getOption(CoAPMessageOptionCode.OptionURIPort));
        assertNull(message.getOption(CoAPMessageOptionCode.OptionURIPath));
        assertNull(message.getOption(CoAPMessageOptionCode.OptionURIQuery));*/
    }

    @Test
    public void onReceive_notAProxyMessage_shouldSkip() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET);
        InetSocketAddress address = new InetSocketAddress("123.123.123.123", 12345);
        Reference<InetSocketAddress> addressReference = new Reference<>(address);

        boolean shouldContinue = proxyLayer.onReceive(message, addressReference);

        assertTrue(shouldContinue);

        verify(client, times(0)).send((CoAPMessage) any(), (CoAPHandler) any());
    }

    @Test
    public void onReceive_proxyRequest_shouldRespondWithProxyingUnsupported() {
        CoAPMessage message = new CoAPMessage(CoAPMessageType.NON, CoAPMessageCode.GET);
        CoAPMessageOption proxyOption = new CoAPMessageOption(CoAPMessageOptionCode.OptionProxyURI, "coaps://123.123.123.123:5555/asd?asd=asd");
        message.addOption(proxyOption);
        InetSocketAddress proxyAddress = new InetSocketAddress("123.123.123.123", 12345);
        Reference<InetSocketAddress> proxyAddressReference = new Reference<>(proxyAddress);

        boolean shouldContinue = proxyLayer.onReceive(message, proxyAddressReference);

        assertFalse(shouldContinue);

        ArgumentCaptor<CoAPMessage> argumentCaptor = ArgumentCaptor.forClass(CoAPMessage.class);
        verify(client).send(argumentCaptor.capture(), (CoAPHandler) isNull());
        CoAPMessage response = argumentCaptor.getValue();
        assertEquals(CoAPMessageCode.CoapCodeProxyingNotSupported, response.getCode());
        assertEquals(CoAPMessageType.NON, response.getType());
        assertEquals(proxyAddress.getAddress().getHostAddress(), response.getURIHost());
        assertEquals((long) proxyAddress.getPort(), (long) response.getURIPort());
    }
}