package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessageType
import org.junit.Assert
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Created by Владимир on 02.06.2017.
 */
class CoAPMessageTest {
    @Test
    fun onSetProxy_shouldAddProxyUriOption() {
        val PROXY_IP = "121.121.121.121"
        val PROXY_PORT = 1234
        val destinationUri = "coap://123.123.123.123:5555"
        val message = CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.GET)
        message.setURI(destinationUri)
        val proxyAddress = InetSocketAddress(PROXY_IP, PROXY_PORT)
        message.setProxy(proxyAddress)
        Assert.assertEquals(destinationUri, message.getOption(CoAPMessageOptionCode.OptionProxyURI)!!.value)
    }
}