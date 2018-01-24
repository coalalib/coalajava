package com.ndmsystems.coala.layers.arq.states;

import com.ndmsystems.coala.layers.arq.Block;
import com.ndmsystems.coala.layers.arq.data.DataFactory;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageType;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Владимир on 20.08.2017.
 */
public class ReceiveStateTest {

    @Test
    public void whenAllBlocksReceived_transferShouldBeCompleted(){
        CoAPMessage initiatingMessage = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST);
        ReceiveState receiveState = new ReceiveState(10, initiatingMessage);
        Block lastBlock = new Block(3, DataFactory.createEmpty(), false);
        Block block0 = new Block(0, DataFactory.createEmpty(), true);
        Block block1 = new Block(1, DataFactory.createEmpty(), true);
        Block block2 = new Block(2, DataFactory.createEmpty(), true);

        receiveState.didReceiveBlock(lastBlock, 10, CoAPMessageCode.CoapCodeContent);
        receiveState.didReceiveBlock(block0, 10, CoAPMessageCode.CoapCodeContinue);
        receiveState.didReceiveBlock(block2, 10, CoAPMessageCode.CoapCodeContinue);
        assertFalse(receiveState.isTransferCompleted());
        receiveState.didReceiveBlock(block1, 10, CoAPMessageCode.CoapCodeContinue);

        assertTrue(receiveState.isTransferCompleted());
    }

    @Test
    public void whenNotAllBlocksReceived_transferShouldNotBeCompleted(){
        CoAPMessage initiatingMessage = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST);
        ReceiveState receiveState = new ReceiveState(10, initiatingMessage);
        Block block0 = new Block(0, DataFactory.createEmpty(), true);
        Block block1 = new Block(1, DataFactory.createEmpty(), true);
        Block block2 = new Block(2, DataFactory.createEmpty(), true);

        receiveState.didReceiveBlock(block0, 10, CoAPMessageCode.CoapCodeContinue);
        receiveState.didReceiveBlock(block2, 10, CoAPMessageCode.CoapCodeContinue);
        receiveState.didReceiveBlock(block1, 10, CoAPMessageCode.CoapCodeContent);

        assertFalse(receiveState.isTransferCompleted());
    }

    @Test
    public void whenAllBlocks_b1b2b3_received_dataShouldBe123(){
        CoAPMessage initiatingMessage = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST);
        ReceiveState receiveState = new ReceiveState(10, initiatingMessage);
        Block block0 = new Block(0, DataFactory.create(new byte[]{1}), true);
        Block block1 = new Block(1, DataFactory.create(new byte[]{2}), true);
        Block block2 = new Block(2, DataFactory.create(new byte[]{3}), false);

        receiveState.didReceiveBlock(block0, 10, CoAPMessageCode.CoapCodeContinue);
        receiveState.didReceiveBlock(block1, 10, CoAPMessageCode.CoapCodeContent);
        receiveState.didReceiveBlock(block2, 10, CoAPMessageCode.CoapCodeContinue);

        byte[] data = receiveState.getData().get();

        assertNotNull(data);
        assertEquals(1, data[0]);
        assertEquals(2, data[1]);
        assertEquals(3, data[2]);
    }

}