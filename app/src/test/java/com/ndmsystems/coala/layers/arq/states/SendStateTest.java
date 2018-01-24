package com.ndmsystems.coala.layers.arq.states;

import com.ndmsystems.coala.layers.arq.Block;
import com.ndmsystems.coala.layers.arq.data.DataFactory;
import com.ndmsystems.coala.layers.arq.data.IData;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageType;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Владимир on 21.08.2017.
 */
public class SendStateTest {

    @Test
    public void onFirstPopBlock_whenBlockSizeIs1_andDataIs123_poppedBlockDataShouldBe1_andNumberShouldBe0_andShouldBeComingMore() {
        CoAPMessage originalMessage = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST);
        SendState sendState = new SendState(DataFactory.create(new byte[]{1, 2, 3}), 10, 1, originalMessage);

        Block poppedBlock = sendState.popBlock();

        assertEquals(0, poppedBlock.getNumber());
        assertTrue(poppedBlock.isMoreComing());

        byte[] blockData = poppedBlock.getData().get();
        assertEquals(1, blockData.length);
        assertEquals(1, poppedBlock.getData().get()[0]);
    }

    @Test
    public void onLastPopBlock_whenBlockSizeIs1_andDataIs123_poppedBlockDataShouldBe3_andNumberShouldBe2_andShouldNotBeComingMore() {
        CoAPMessage originalMessage = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST);
        SendState sendState = new SendState(DataFactory.create(new byte[]{1, 2, 3}), 10, 1, originalMessage);

        sendState.popBlock();
        sendState.popBlock();
        Block lastPoppedBlock = sendState.popBlock();

        assertEquals(2, lastPoppedBlock.getNumber());
        assertFalse(lastPoppedBlock.isMoreComing());

        byte[] blockData = lastPoppedBlock.getData().get();
        assertEquals(1, blockData.length);
        assertEquals(3, lastPoppedBlock.getData().get()[0]);
    }

    @Test
    public void onFirstBlockTransfered_whenBlockSizeIs1_andDataIs123_shouldNotBeCompleted(){
        CoAPMessage originalMessage = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST);
        SendState sendState = new SendState(DataFactory.create(new byte[]{1, 2, 3}), 10, 1, originalMessage);

        Block firstBlock = sendState.popBlock();
        sendState.didTransmit(firstBlock.getNumber());

        assertFalse(sendState.isCompleted());
    }

    @Test
    public void onAllBlocksTransfered_whenBlockSizeIs1_andDataIs123_shouldBeCompleted(){
        CoAPMessage originalMessage = new CoAPMessage(CoAPMessageType.CON, CoAPMessageCode.POST);
        SendState sendState = new SendState(DataFactory.create(new byte[]{1, 2, 3}), 10, 1, originalMessage);

        Block block = sendState.popBlock();
        sendState.didTransmit(block.getNumber());
        block = sendState.popBlock();
        sendState.didTransmit(block.getNumber());
        block = sendState.popBlock();
        sendState.didTransmit(block.getNumber());

        assertTrue(sendState.isCompleted());
    }
}