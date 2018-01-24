package com.ndmsystems.coala.layers.arq;

import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.helpers.MessageHelper;
import com.ndmsystems.coala.helpers.StringHelper;
import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.layers.ReceiveLayer;
import com.ndmsystems.coala.layers.SendLayer;
import com.ndmsystems.coala.layers.arq.data.DataFactory;
import com.ndmsystems.coala.layers.arq.data.IData;
import com.ndmsystems.coala.layers.arq.states.LoggableState;
import com.ndmsystems.coala.layers.arq.states.ReceiveState;
import com.ndmsystems.coala.layers.arq.states.SendState;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessagePayload;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.utils.Reference;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by bas on 02.08.17.
 */

public class ArqLayer implements ReceiveLayer, SendLayer {

    private static final int WINDOW_SIZE = 70;
    private static final int MAX_PAYLOAD_SIZE = 512;

    private final CoAPClient client;
    private final CoAPMessagePool messagePool;

    protected Map<String, ReceiveState> receiveStates = new ConcurrentHashMap<>();
    protected Map<String, SendState> sendStates = new ConcurrentHashMap<>();

    public ArqLayer(CoAPClient client,
                    CoAPMessagePool messagePool) {
        this.client = client;
        this.messagePool = messagePool;
    }

    private boolean isBlockedMessage(CoAPMessage message) {
        return message.hasOption(CoAPMessageOptionCode.OptionBlock1) ||
                message.hasOption(CoAPMessageOptionCode.OptionBlock2);
    }

    private boolean isAboutArq(CoAPMessage message) {
        return message.hasOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize);
    }

    private Block getBlock1(CoAPMessage message) {
        CoAPMessagePayload payload = message.getPayload();
        IData data = payload == null ? null : DataFactory.create(message.getPayload().content);
        return new Block((int) message.getOption(CoAPMessageOptionCode.OptionBlock1).value,
                data);
    }

    private Block getBlock2(CoAPMessage message) {
        CoAPMessagePayload payload = message.getPayload();
        IData data = payload == null ? null : DataFactory.create(message.getPayload().content);
        return new Block((int) message.getOption(CoAPMessageOptionCode.OptionBlock2).value,
                data);
    }

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) {

        if (!isAboutArq(message))
            return true;

        if (message.getCode() == CoAPMessageCode.CoapCodeEmpty &&
                message.getType() == CoAPMessageType.ACK) {
            messagePool.remove(message.getId());
            return false;
        }

        if(!isBlockedMessage(message))
            return true;

        if (message.getToken() == null)
            sendResetMessage(message, senderAddressReference.get());

        int windowSize = (int) message.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize).value;

        CoAPMessage ackMessage = CoAPMessage.ackTo(message, senderAddressReference.get(), CoAPMessageCode.CoapCodeEmpty);

        if (message.hasOption(CoAPMessageOptionCode.OptionBlock1)) {
            Block block = getBlock1(message);
            return process(message, CoAPMessageOptionCode.OptionBlock1, block, windowSize, ackMessage);
        }

        if (message.hasOption(CoAPMessageOptionCode.OptionBlock2)) {
            Block block = getBlock2(message);
            return process(message, CoAPMessageOptionCode.OptionBlock2, block, windowSize, ackMessage);
        }
        return true;
    }

    private void sendResetMessage(CoAPMessage incomingMessage, InetSocketAddress fromAddress) {
        CoAPMessage resetMessage = CoAPMessage.resetTo(incomingMessage, fromAddress);
        client.send(resetMessage).subscribe(
                response -> {
                    //ignore
                },
                throwable -> {
                    //ignore
                }
        );
    }

    private boolean process(CoAPMessage incomingMessage, CoAPMessageOptionCode blockOptionCode, Block block, int windowSize, CoAPMessage ackMessage) {
        String token = Hex.encodeHexString(incomingMessage.getToken());

        switch (incomingMessage.getType()) {
            case ACK:
            case RST:
                // Transmit ACK
                didTransmit(block.getNumber(), token);
                messagePool.remove(incomingMessage.getId());
                SendState sendState = sendStates.get(token);
                if (sendState != null && sendState.isCompleted()) {
                    LogHelper.v("ARQ: Sending completed, pushing to message pool original message" + sendState.getOriginalMessage().getId());
                    logArqTransmission(sendState);
                    incomingMessage = sendState.getOriginalMessage();
                    sendStates.remove(token);
                    return true;
                }
                break;
            case CON:
                // Receive CON
                CoAPMessagePayload payload = incomingMessage.getPayload();
                if (payload == null) {
                    LogHelper.e("ARQ: payload expected for token = " + token);
                    throw new RuntimeException("ARQ: payload expected for token = " + token);
                }

                ReceiveState receiveState = receiveStates.get(token);

                if (receiveState == null) {
                    LogHelper.v("ARQ: creating ReceiveState for token = " + token);
                    receiveState = new ReceiveState(windowSize, incomingMessage);
                    receiveStates.put(token, receiveState);
                }

                receiveState.didReceiveBlock(block, windowSize, incomingMessage.getCode());

                ackMessage.addOption(new CoAPMessageOption(blockOptionCode, block.toInt()));
                ackMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, windowSize));

                if (receiveState.isTransferCompleted()) {
                    LogHelper.v("ARQ: Receive completed, passing message " + incomingMessage.getId() + " along");
                    logArqTransmission(receiveState);
                    receiveStates.remove(token);
                    incomingMessage.setPayload(new CoAPMessagePayload(receiveState.getData().get()));
                    incomingMessage.setCode(receiveState.getInitiatingMessage().getCode());
                    //client.send(ackMessage, null);
                    return true;
                } else {
                    LogHelper.v("ARQ: Receive in progress, responding with ACK: continued");
                    ackMessage.setCode(CoAPMessageCode.CoapCodeContinue);
                    client.send(ackMessage, null);
                }
                break;
            case NON:
                LogHelper.e("ARQ: NON message received");
                throw new RuntimeException("ARQ: NON message received");
        }
        incomingMessage.removeOption(blockOptionCode);
        return false;
    }

    private void didTransmit(int blockNumber, String token) {
        LogHelper.v("ARQ: did transmit block = " + blockNumber + " for token = " + token);
        SendState sendState = sendStates.get(token);
        sendState.didTransmit(blockNumber);
        sendMoreData(token);
    }

    private void sendMoreData(String token) {
        LogHelper.v("ARQ: send more data for token = " + token);
        SendState state = sendStates.get(token);
        Block block;
        while ((block = state.popBlock()) != null) {
            LogHelper.v("ARQ: did pop block number = " + block.getNumber());
            send(block, state.getOriginalMessage(), token, state);
            state.incrementNumberOfMessage();
            sendStates.put(token, state);
        }
    }

    private void send(Block block, CoAPMessage originalMessage, String token, SendState state) {
        CoAPMessage blockMessage = new CoAPMessage(originalMessage.getType(), originalMessage.getCode());
        blockMessage.setOptions(originalMessage.getOptions());

        CoAPMessageOptionCode blockCode = originalMessage.isRequest() ? CoAPMessageOptionCode.OptionBlock1 : CoAPMessageOptionCode.OptionBlock2;
        CoAPMessageOption blockOption = new CoAPMessageOption(blockCode, block.toInt());
        blockMessage.addOption(blockOption);

        CoAPMessageOption srOption = new CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, state.getWindowSize());
        blockMessage.addOption(srOption);

        blockMessage.setToken(Hex.decodeHex(token.toCharArray()));
        blockMessage.setPayload(new CoAPMessagePayload(block.getData().get()));
        blockMessage.setURI(originalMessage.getURI());
        if (originalMessage.hasOption(CoAPMessageOptionCode.OptionProxyURI))
            blockMessage.addOption(originalMessage.getOption(CoAPMessageOptionCode.OptionProxyURI));
        if (originalMessage.getProxy() != null)
            blockMessage.setProxy(originalMessage.getProxy());
        blockMessage.setResendHandler(state);
        client.send(blockMessage).subscribe(
                response -> {
                    //ignore
                },
                throwable -> {
                    LogHelper.v("Block number = " + block.getNumber() + " failed");
                    fail(token);
                }
        );
    }

    private void fail(String token) {
        LogHelper.v("ARQ: fail to transfer for token = " + token);
        SendState sendState = sendStates.get(token);
        if (sendState != null) {
            sendState.onError();
            sendStates.remove(token);
        }
        receiveStates.remove(token);
    }

    @Override
    public boolean onSend(CoAPMessage message, Reference<InetSocketAddress> receiverAddressReference) {
        if (hasWindowSizeOption(message)
                && !isStartMixingModeMessage(message))
            return true;

        if (!isBiggerThenCanBeTransferedBySingleBlock(message) ||
                message.getToken() == null)
            return true;


        String token = Hex.encodeHexString(message.getToken());
        CoAPMessagePayload payload = message.getPayload();

        LogHelper.v("ARQ: removing original message " + message.getId() + " from pool");
        messagePool.remove(message.getId());
        CoAPMessage originalMessage = null;
        CoAPMessage ackMessage = null;
        switch (message.getType()) {
            case ACK:
            case RST:
                originalMessage = new CoAPMessage(message);
                originalMessage.setType(CoAPMessageType.CON);
                originalMessage.removeOption(CoAPMessageOptionCode.OptionBlock1);
                CoAPMessage.convertToEmptyAck(message, receiverAddressReference.get());
                ackMessage = message;
                ackMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, WINDOW_SIZE));
                LogHelper.d("ARQ: Send empty ack, id " + ackMessage.getId() + ", payload: '" + ackMessage.toString() + "', destination host: " + ackMessage.getURI() + " type " + ackMessage.getType() + " code " + ackMessage.getCode().name() + " token " + Hex.encodeHexString(ackMessage.getToken())
                        + "\n" + "Options: " + MessageHelper.getMessageOptionsString(ackMessage));
                break;
            case NON:
                originalMessage = new CoAPMessage(message);
                originalMessage.setType(CoAPMessageType.CON);
                originalMessage.removeOption(CoAPMessageOptionCode.OptionBlock1);
                break;
            case CON:
                originalMessage = new CoAPMessage(message);
                break;
        }
        SendState sendState = new SendState(DataFactory.create(payload.content), WINDOW_SIZE, MAX_PAYLOAD_SIZE, originalMessage);
        sendStates.put(token, sendState);
        sendMoreData(token);

        LogHelper.v("ARQ: splitting message " + message.getId() + " to values. Sending payload = " + payload.content.length);

        if (ackMessage != null)
            return true;

        return false;
    }

    private boolean isStartMixingModeMessage(CoAPMessage message) {
        return !message.getCode().isRequest()
                && message.hasOption(CoAPMessageOptionCode.OptionBlock1)
                && !message.hasOption(CoAPMessageOptionCode.OptionBlock2)
                && isBiggerThenCanBeTransferedBySingleBlock(message);
    }

    private boolean hasWindowSizeOption(CoAPMessage message) {
        return message.hasOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize);
    }

    private boolean isBiggerThenCanBeTransferedBySingleBlock(CoAPMessage message) {
        return message.getPayload() != null &&
                message.getPayload().content != null &&
                message.getPayload().content.length > MAX_PAYLOAD_SIZE;
    }

    private void logArqTransmission(LoggableState state) {
        LogHelper.w("ARQ " + (state.isIncoming() ? "rx" : "tx") + " transfer " + Hex.encodeHexString(state.getToken())
                + " " + StringHelper.getHumanReadableByteString(state.getDataSize())
                + " at " + StringHelper.getHumanReadableByteString(state.getSpeed()) + "/s"
                + (state.getPercentOfLoss() != null ? ", " + state.getPercentOfLoss() + "% loss" : ""));
    }

}
