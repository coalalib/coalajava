package com.ndmsystems.coala.layers.arq;

import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.coala.layers.arq.data.DataFactory;
import com.ndmsystems.coala.layers.arq.data.IData;
import com.ndmsystems.coala.layers.arq.states.ReceiveState;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessagePayload;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.utils.Reference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Created by Владимир on 21.08.2017.
 */
public class ArqLayerTest {
    private CoAPClient client;
    private ArqLayer layer;
    private CoAPMessagePool messagePool;

    @Before
    public void setUp() throws Exception {
        client = mock(CoAPClient.class);
        messagePool = mock(CoAPMessagePool.class);
        layer = new ArqLayer(client, messagePool);
    }

    @Test
    public void onReceiveLastArqMessageOfRequest_shouldRemoveReceiveState() {
        Reference<InetSocketAddress> senderAddressReference = new Reference<>(new InetSocketAddress("111.111.111.111", 111));
        CoAPMessage firstMessage = arqMessageOfRequest(0, false, null);
        CoAPMessage lastMessage = arqMessageOfRequest(1, true, null);

        layer.onReceive(firstMessage, senderAddressReference);
        layer.onReceive(lastMessage, senderAddressReference);

        assertEquals(0, layer.receiveStates.size());
    }

    @Test
    public void onReceiveLastArqMessageOfRequest_shouldCorrectlyComposeData() throws UnsupportedEncodingException {
        Reference<InetSocketAddress> senderAddressReference = new Reference<>(new InetSocketAddress("111.111.111.111", 111));
        String data = "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";
        byte[] bytes = data.getBytes();
        CoAPMessage firstMessage = arqMessageOfRequest(0, false, DataFactory.create(Arrays.copyOfRange(bytes, 0, 512)));
        CoAPMessage lastMessage = arqMessageOfRequest(1, true, DataFactory.create(Arrays.copyOfRange(bytes, 512, 660)));

        layer.onReceive(firstMessage, senderAddressReference);
        layer.onReceive(lastMessage, senderAddressReference);

        String result = lastMessage.getPayload().toString();
        assertEquals(data, result);
    }

    @Test
    public void onReceiveLastArqMessageOfRequest_shouldPassMessageToNextLayer() {
        Reference<InetSocketAddress> senderAddressReference = new Reference<>(new InetSocketAddress("111.111.111.111", 111));
        CoAPMessage firstRequest = arqMessageOfRequest(0, false, null);
        Block block = new Block((Integer) firstRequest.getOption(CoAPMessageOptionCode.OptionBlock1).value, DataFactory.create(firstRequest.getPayload().content));
        assertEquals(0, block.getNumber());

        CoAPMessage lastRequest = arqMessageOfRequest(1, true, null);

        layer.onReceive(firstRequest, senderAddressReference);
        boolean passToNextLayer = layer.onReceive(lastRequest, senderAddressReference);

        assertTrue(passToNextLayer);
    }

    @Test
    public void onReceiveFirstArqMessageOfRequest_shouldCreateReceiveState_andSendAck_andDoesntPassMessageToOtherLayers() {
        CoAPMessage firstRequest = arqMessageOfRequest(0, false, null);
        CoAPMessageOption requestBlock1Option = firstRequest.getOption(CoAPMessageOptionCode.OptionBlock1);
        Reference<InetSocketAddress> senderAddressReference = new Reference<>(new InetSocketAddress("111.111.111.111", 111));

        //receive first arq message of request
        boolean passToOtherLayers = layer.onReceive(firstRequest, senderAddressReference);

        //verify create receive state
        assertEquals(1, layer.receiveStates.size());
        ReceiveState receiveState = (ReceiveState) layer.receiveStates.values().toArray()[0];

        //verify transfer is not completed
        assertFalse(receiveState.isTransferCompleted());

        //verify send ack
        ArgumentCaptor<CoAPMessage> ackCaptor = ArgumentCaptor.forClass(CoAPMessage.class);
        verify(client).send(ackCaptor.capture(), isNull());
        CoAPMessage ackMessage = ackCaptor.getValue();
        assertTrue(isAckCorrect(firstRequest, senderAddressReference.get(), ackMessage, requestBlock1Option));

        //verify doesn't pass to other layers
        assertFalse(passToOtherLayers);
    }

    private CoAPMessage arqMessageOfRequest(int blockNumber, boolean last, IData data) {
        CoAPMessage request = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST);
        request.setToken(new byte[]{1, 2, 3});
        request.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, 10));
        request.setURI("coap://123.123.123.123:123/na?ma=ha");
        Block block = new Block(blockNumber, data == null ? DataFactory.create(new byte[512]) : data, !last);
        request.setPayload(new CoAPMessagePayload(block.getData().get()));
        CoAPMessageOption block1Option = new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, block.toInt());
        request.addOption(block1Option);
        return request;
    }

    private boolean isAckCorrect(CoAPMessage request, InetSocketAddress senderAddress, CoAPMessage ackMessage, CoAPMessageOption requestBlock1Option) {
        assertEquals(CoAPMessageType.ACK, ackMessage.getType());
        assertEquals(senderAddress.getPort(), (int) ackMessage.getURIPort());
        assertEquals(senderAddress.getAddress().getHostAddress(), ackMessage.getURIHost());

        Block block = new Block((int) requestBlock1Option.value, DataFactory.create(request.getPayload().content));
        CoAPMessageOption ackBlockOption = ackMessage.getOption(CoAPMessageOptionCode.OptionBlock1);

        if (block.isMoreComing())
            assertEquals(CoAPMessageCode.CoapCodeContinue, ackMessage.getCode());
        else
            assertEquals(CoAPMessageCode.CoapCodeEmpty, ackMessage.getCode());

        assertEquals((int) requestBlock1Option.value, (int) ackBlockOption.value);

        return true;
    }
}