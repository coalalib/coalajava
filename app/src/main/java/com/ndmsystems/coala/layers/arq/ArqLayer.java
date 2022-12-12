package com.ndmsystems.coala.layers.arq;

import com.ndmsystems.coala.BuildConfig;
import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPHandler;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.coala.helpers.MessageHelper;
import com.ndmsystems.coala.layers.ReceiveLayer;
import com.ndmsystems.coala.layers.SendLayer;
import com.ndmsystems.coala.layers.arq.data.DataFactory;
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
import com.ndmsystems.infrastructure.logging.LogHelper;

import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by bas on 02.08.17.
 */

public class ArqLayer implements ReceiveLayer, SendLayer {

    private static final int WINDOW_SIZE = 70;
    private static final int MAX_PAYLOAD_SIZE = 1024;

    private final CoAPClient client;
    private final CoAPMessagePool messagePool;

    private final Map<String, ReceiveState> receiveStates = ExpiringMap.builder()
            .expirationPolicy(ExpirationPolicy.ACCESSED)
            .expiration(10, TimeUnit.SECONDS)
        .build();
    private final Map<String, SendState> sendStates = new ConcurrentHashMap<>();

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
        byte[] data = payload == null ? null : message.getPayload().content;
        return new Block((int) message.getOption(CoAPMessageOptionCode.OptionBlock1).value,
                data);
    }

    private Block getBlock2(CoAPMessage message) {
        CoAPMessagePayload payload = message.getPayload();
        byte[] data = payload == null ? null : message.getPayload().content;
        return new Block((int) message.getOption(CoAPMessageOptionCode.OptionBlock2).value,
                data);
    }

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) {

        if (!isAboutArq(message)) {
            return true;
        }

        if (message.getCode() == CoAPMessageCode.CoapCodeEmpty
                && message.getType() == CoAPMessageType.ACK && !message.hasOption(CoAPMessageOptionCode.OptionBlock1)) {//For block1 block2 mixed mode, message ending block1 start block2, like Received data from Peer, id 51486, payload:'', address: 138.197.191.160:5683 type: ACK code: CoapCodeEmpty path: null schema: coap token 951e67cdc72af162
            //Options: OptionBlock1 : 'com.ndmsystems.coala.layers.arq.Block@a046fab'(8174) OptionSelectiveRepeatWindowSize : '70', address: 138.197.191.160:5683 type: CON code: CoapCodeContent path: null schema: coap token 951e67cdc72af162
            //Options: OptionBlock2 : 'com.ndmsystems.coala.layers.arq.Block@72bd608'(30) OptionSelectiveRepeatWindowSize : '300'
            messagePool.setNoNeededSending(message);
            return false;
        }

        if (!isBlockedMessage(message)) {
            return true;
        }

        if (message.getToken() == null) {
            sendResetMessage(message, senderAddressReference.get());
        }

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
        client.send(resetMessage, null);
    }

    private boolean process(CoAPMessage incomingMessage, CoAPMessageOptionCode blockOptionCode, Block block, int windowSize, CoAPMessage ackMessage) {
        String token = Hex.encodeHexString(incomingMessage.getToken());

        switch (incomingMessage.getType()) {
            case ACK:
            case RST:
                // Transmit ACK
                didTransmit(block.getNumber(), token);
                messagePool.remove(incomingMessage);
                SendState sendState = sendStates.get(token);
                if (sendState != null && sendState.isCompleted()) {
                    LogHelper.v("ARQ: Sending completed, pushing to message pool original message" + sendState.getOriginalMessage().getId());
                    CoAPMessage originalMessage = sendState.getOriginalMessage();
                    if (incomingMessage.getCode() == CoAPMessageCode.CoapCodeEmpty
                            && incomingMessage.getType() == CoAPMessageType.ACK) {
                        messagePool.add(originalMessage);
                        messagePool.setNoNeededSending(originalMessage);
                        sendStates.remove(token);
                        return false;

                    } else {
                        incomingMessage = originalMessage;
                        sendStates.remove(token);
                        return true;
                    }
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
                    receiveState = new ReceiveState(incomingMessage);
                    receiveStates.put(token, receiveState);
                }

                receiveState.didReceiveBlock(block, incomingMessage.getCode());

                ackMessage.addOption(new CoAPMessageOption(blockOptionCode, block.toInt()));
                ackMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, windowSize));

                if (receiveState.isTransferCompleted()) {
                    //receiveStates.remove(token);

                    CoAPMessage originalMessage = messagePool.getSourceMessageByToken(incomingMessage.getHexToken());
                    if (originalMessage != null) {
                        LogHelper.v("ARQ: Receive " + incomingMessage.getHexToken() + " completed, size " + receiveState.getDataSize()
                                + ", passing message " + originalMessage.getId() + ", with token: " + Hex.encodeHexString(originalMessage.getToken())
                                + " along");
                        if (originalMessage.hasOption(CoAPMessageOptionCode.OptionProxyURI)) {
                            ackMessage.addOption(originalMessage.getOption(CoAPMessageOptionCode.OptionProxyURI));
                        }
                        if (originalMessage.getProxy() != null) {
                            ackMessage.setProxy(originalMessage.getProxy());
                            ackMessage.setProxySecurityId(originalMessage.getProxySecurityId());
                        }
                        incomingMessage.setId(originalMessage.getId());
                    } else {
                        LogHelper.v("ARQ: Receive with originalMessage = null, token " + incomingMessage.getHexToken() + " completed, size "
                                + receiveState.getDataSize());
                    }
                    ackMessage.setCode(CoAPMessageCode.CoapCodeEmpty);
                    client.send(ackMessage, null);

                    //incomingMessage = messagePool.getSourceMessageByToken(incomingMessage.getHexToken());
                    incomingMessage.setPayload(new CoAPMessagePayload(receiveState.getData()));
                    incomingMessage.setCode(CoAPMessageCode.CoapCodeContent);
                    incomingMessage.setType(CoAPMessageType.ACK);

                    return true;
                } else {
                    if (BuildConfig.DEBUG) { //For no slowing prod version, so many logs, what don't showing anymore
                        LogHelper.v("ARQ: Receive " + incomingMessage.getHexToken() + " in progress, responding with ACK continued, received: "
                                + receiveState.getDataSize());
                    }
                    ackMessage.setCode(CoAPMessageCode.CoapCodeContinue);

                    CoAPMessage originalMessage = messagePool.getSourceMessageByToken(incomingMessage.getHexToken());

                    if (originalMessage != null) {
                        if (originalMessage.hasOption(CoAPMessageOptionCode.OptionProxyURI)) {
                            ackMessage.addOption(originalMessage.getOption(CoAPMessageOptionCode.OptionProxyURI));
                        }
                        if (originalMessage.getProxy() != null) {
                            ackMessage.setProxy(originalMessage.getProxy());
                        }
                    }

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
        if (sendState != null) {
            sendState.didTransmit(blockNumber);
            sendMoreData(token);
        }
    }

    private void sendMoreData(String token) {
        SendState state = sendStates.get(token);
        Block block;
        if (state != null) {
            while ((block = state.popBlock()) != null) {
                LogHelper.v("ARQ: did pop block number = " + block.getNumber());
                send(block, state.getOriginalMessage(), token, state);
                state.incrementNumberOfMessage();
                sendStates.put(token, state);
            }
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
        blockMessage.setPayload(new CoAPMessagePayload(block.getData()));
        blockMessage.setURI(originalMessage.getURI());
        if (originalMessage.hasOption(CoAPMessageOptionCode.OptionProxyURI)) {
            blockMessage.addOption(originalMessage.getOption(CoAPMessageOptionCode.OptionProxyURI));
        }
        if (originalMessage.getProxy() != null) {
            blockMessage.setProxy(originalMessage.getProxy());
        }
        blockMessage.setResendHandler(state);
        client.send(blockMessage, new CoAPHandler() {
            @Override
            public void onMessage(CoAPMessage message, String error) {
                if (error != null) {
                    LogHelper.v("Block number = " + block.getNumber() + " failed " + error);
                    fail(token);
                    return;
                }
                LogHelper.v("Block number = " + block.getNumber() + " sent");
            }

            @Override
            public void onAckError(String error) {
                LogHelper.v("Block number = " + block.getNumber() + " failed");
                fail(token);
            }
        });

    }

    private void fail(String token) {
        LogHelper.v("ARQ: fail to transfer for token = " + token);
        SendState sendState = sendStates.get(token);
        if (sendState != null) {
            sendState.onError(client.getMessageDeliveryInfo(sendState.getOriginalMessage()));
            sendStates.remove(token);
        }
        receiveStates.remove(token);
    }

    @Override
    public boolean onSend(CoAPMessage message, Reference<InetSocketAddress> receiverAddressReference) {
        if (hasWindowSizeOption(message) && !isStartMixingModeMessage(message)) {
            return true;
        }

        if (!isBiggerThenCanBeTransferedBySingleBlock(message) || message.getToken() == null) {
            return true;
        }


        String token = Hex.encodeHexString(message.getToken());
        CoAPMessagePayload payload = message.getPayload();

        LogHelper.v("ARQ: removing original message " + message.getId() + " from pool");
        messagePool.remove(message);
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
                LogHelper.d(
                        "ARQ: Send empty ack, id " + ackMessage.getId() + " " +
                                "payload: '" + ackMessage + " " +
                                "destination host: " + ackMessage.getURI() + " " +
                                "type: " + ackMessage.getType() + " " +
                                "code: " + ackMessage.getCode().name() + " " +
                                "token: " + Hex.encodeHexString(ackMessage.getToken()) + " " +
                                "options: " + MessageHelper.getMessageOptionsString(ackMessage));
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

        LogHelper.v("ARQ: split message " + message.getId() + " to values. Sending payload = " + payload.content.length);

        return ackMessage != null;
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

    public LoggableState getArqReceivingStateForToken(final byte[] token) {
        return receiveStates.get(Hex.encodeHexString(token));
    }

}
