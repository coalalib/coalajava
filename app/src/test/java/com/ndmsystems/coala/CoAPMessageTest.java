package com.ndmsystems.coala;

import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessageType;

import org.junit.Test;

import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;

/**
 * Created by Владимир on 02.06.2017.
 */
public class CoAPMessageTest {

    @Test
    public void onSetProxy_shouldAddProxyUriOption() {
        final String PROXY_IP = "121.121.121.121";
        final int PROXY_PORT = 1234;
        final String destinationUri = "coap://123.123.123.123:5555";
        CoAPMessage message = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET);
        message.setURI(destinationUri);
        InetSocketAddress proxyAddress = new InetSocketAddress(PROXY_IP, PROXY_PORT);

        message.setProxy(proxyAddress);

        assertEquals(destinationUri, message.getOption(CoAPMessageOptionCode.OptionProxyURI).value);
    }

}