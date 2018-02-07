package com.ndmsystems.coala.layers.blockwise;

import com.ndmsystems.coala.CoAPClient;
import com.ndmsystems.coala.CoAPHandler;
import com.ndmsystems.coala.CoAPMessagePool;
import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.layers.ReceiveLayer;
import com.ndmsystems.coala.layers.SendLayer;
import com.ndmsystems.coala.layers.blockwise.interfaces.IBlockwiseInput;
import com.ndmsystems.coala.layers.blockwise.interfaces.IBlockwiseOutput;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageOption;
import com.ndmsystems.coala.message.CoAPMessageOptionCode;
import com.ndmsystems.coala.message.CoAPMessagePayload;
import com.ndmsystems.coala.message.CoAPMessageType;
import com.ndmsystems.coala.observer.ObservingResource;
import com.ndmsystems.coala.observer.RegistryOfObservingResources;
import com.ndmsystems.coala.utils.Reference;

import java.net.InetSocketAddress;

public class BlockwiseLayer implements ReceiveLayer, SendLayer {
    public static final Integer MAX_PAYLOAD_SIZE = 512;

    private IBlockwiseInput blockwiseInput = BlockwiseInputPool.getBlockwiseInput();
    private IBlockwiseOutput blockwiseOutput = BlockwiseOutputPool.getBlockwiseOutput();

    private CoAPMessagePool messagePool;
    private CoAPClient client;
    private RegistryOfObservingResources registryOfObservingResources;

    public BlockwiseLayer(CoAPMessagePool messagePool,
                          CoAPClient client,
                          RegistryOfObservingResources registryOfObservingResources) {
        this.messagePool = messagePool;
        this.client = client;
        this.registryOfObservingResources = registryOfObservingResources;
    }

    @Override
    public boolean onReceive(CoAPMessage message, Reference<InetSocketAddress> senderAddressReference) {
        try {
            CoAPMessageOption block2Option = message.getOption(CoAPMessageOptionCode.OptionBlock2);

            if (block2Option != null) {
                LogHelper.d("Block2Option: " + (Block.fromInt((int) block2Option.value).toString()));
                Block block2 = Block.fromInt((int) block2Option.value);

                boolean block2result = processBlock2(message, senderAddressReference.get(), block2);
                if (!block2result) return false;
            }

            CoAPMessageOption block1Option = message.getOption(CoAPMessageOptionCode.OptionBlock1);

            if (block1Option != null) {
                LogHelper.d("Block1Option: " + (Block.fromInt((int) block1Option.value).toString()));
                Block block1 = Block.fromInt((int) block1Option.value);

                boolean block1result = processBlock1(message, senderAddressReference.get(), block1);
                if (!block1result) return false;
            }

            return true;
        } catch (Throwable th) {
            LogHelper.i("Error: " + th.toString());
            sendResetMessage(senderAddressReference.get(), message);
            return false;
        }
    }

    private void sendResetMessage(InetSocketAddress senderAddress, CoAPMessage message) {
        LogHelper.v("Send reset message");
        CoAPMessage responseMessage = new CoAPMessage(CoAPMessageType.RST, CoAPMessageCode.CoapCodeEmpty, message.getId());
        if (message.getToken() != null) responseMessage.setToken(message.getToken());

        responseMessage.setURIHost(senderAddress.getAddress().getHostAddress());
        responseMessage.setURIPort(senderAddress.getPort());

        if (message.getOption(CoAPMessageOptionCode.OptionBlock1) != null) {
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, message.getOption(CoAPMessageOptionCode.OptionBlock1).value));
        }

        // Validate message scheme
        responseMessage.setURIScheme(message.getURIScheme());

        client.send(responseMessage, null);
    }

    private boolean processBlock2(CoAPMessage message, InetSocketAddress senderAddress, Block block2) throws Throwable {
        final String token = Hex.encodeHexString(message.getToken());
        if (message.getCode().isRequest()) {
            LogHelper.d("Request");
            return processBlockwiseOutput(message, senderAddress, token, block2, false);
        } else {
            LogHelper.d("Response");
            return processBlockwiseInput(senderAddress, message, token, block2, false);
        }
    }

    private boolean processBlock1(CoAPMessage message, InetSocketAddress senderAddress, Block block1) throws Throwable {
        final String token = Hex.encodeHexString(message.getToken());
        LogHelper.d("Block1: " + block1.blockNumber + " " + block1.moreBlocks + " " + block1.blockSize);

        if (message.isRequest()) {
            LogHelper.d("Request");
            return processBlockwiseInput(senderAddress, message, token, block1, true);
        } else {
            LogHelper.d("Response");
            return processBlockwiseOutput(message, senderAddress, token, block1, true);
        }
    }

    private boolean processBlockwiseInput(InetSocketAddress senderAddress, CoAPMessage message, final String token, Block block, boolean isBlock1) throws Throwable {
        if (block.blockNumber == 0) { // It's a first message
            saveMessage(message, token, isBlock1);
        }

        if (block.blockNumber <= blockwiseInput.getLastReceivedBlock(token) || (blockwiseInput.getLastReceivedBlock(token) == -1 && block.blockNumber != 0)) {
            LogHelper.d("Ignore block");
            return false; // Нашлось потерянное ранее и уже полученное добро!
        }

        if (message.getPayload() != null)
            blockwiseInput.addPart(token, message.getPayload().content);

        messagePool.remove(message);

        if (message.getType() == CoAPMessageType.CON)
            sendAckMessage(senderAddress, message.getToken(), message);

        CoAPMessage mainMessage = blockwiseInput.getMessage(token);
        if (block.moreBlocks) {
            getNextBlock(mainMessage, token, block, isBlock1);

            return false;
        } else {
            addDataToMessageFromMain(message, mainMessage);

            blockwiseInput.remove(token);

            return true;
        }
    }

    private void sendAckMessage(InetSocketAddress senderAddress, byte[] byteToken, CoAPMessage message) {
        LogHelper.v("Send ack message");
        CoAPMessage responseMessage = new CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent, message.getId());
        if (byteToken != null)
            responseMessage.setToken(byteToken);

        responseMessage.setURIHost(senderAddress.getAddress().getHostAddress());
        responseMessage.setURIPort(senderAddress.getPort());

        if (message.getOption(CoAPMessageOptionCode.OptionBlock1) != null)
            responseMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, message.getOption(CoAPMessageOptionCode.OptionBlock1).value));

        // Validate message scheme
        responseMessage.setURIScheme(message.getURIScheme());

        client.send(responseMessage, null);
    }

    private void getNextBlock(CoAPMessage mainMessage, final String token, Block block, boolean isBlock1) {
        CoAPMessage getNextBlockMessage;
        if (isBlock1) {
            getNextBlockMessage = new CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContinue, mainMessage.getId());

            if (mainMessage.hasOption(CoAPMessageOptionCode.OptionProxyURI))
                getNextBlockMessage.addOption(mainMessage.getOption(CoAPMessageOptionCode.OptionProxyURI));
            if (mainMessage.getProxy() != null)
                getNextBlockMessage.setProxy(mainMessage.getProxy());

            getNextBlockMessage.setURI(mainMessage.getURI());

            getNextBlockMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, block.toInt()));
        } else {
            block.blockNumber++;
            getNextBlockMessage = new CoAPMessage(CoAPMessageType.CON, mainMessage.getCode());

            if (mainMessage.hasOption(CoAPMessageOptionCode.OptionProxyURI))
                getNextBlockMessage.addOption(mainMessage.getOption(CoAPMessageOptionCode.OptionProxyURI));
            if (mainMessage.getProxy() != null)
                getNextBlockMessage.setProxy(mainMessage.getProxy());

            getNextBlockMessage.setURI(mainMessage.getURI());

            getNextBlockMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock2, block.toInt()));
        }
        LogHelper.d("Try to get next block " + block + " token = " + token);

        getNextBlockMessage.setToken(mainMessage.getToken());
        client.send(getNextBlockMessage, new CoAPHandler() {
            @Override
            public void onMessage(CoAPMessage message, String error) {
                if (error != null) {
                    LogHelper.w("Error then get next block2: " + error);
                    blockwiseInput.remove(token);
                }
            }

            @Override
            public void onAckError(String error) {
                LogHelper.w("Error then get next block2: " + error);
                blockwiseInput.remove(token);
            }
        });
    }

    private boolean processBlockwiseOutput(CoAPMessage message, InetSocketAddress senderAddress, String token, Block block, boolean isBlock1) {
        LogHelper.d("Not ACK");

        messagePool.remove(message);

        if (blockwiseOutput.isDataAvailable(token)) {
            // validate message address
            CoAPMessage outputMessage;
            if (isBlock1) {
                block.blockNumber++;
                CoAPMessage mainMessage = blockwiseOutput.getMessage(token);
                outputMessage = new CoAPMessage(CoAPMessageType.CON, mainMessage.getCode());
                outputMessage.setOptions(mainMessage.getOptions());
                outputMessage.setAddress(mainMessage.getAddress());

                if (mainMessage.getProxy() != null) outputMessage.setProxy(mainMessage.getProxy());
            } else {
                outputMessage = new CoAPMessage(CoAPMessageType.ACK, CoAPMessageCode.CoapCodeContent, message.getId());

                outputMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIHost, senderAddress.getHostName()));
                outputMessage.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionURIPort, senderAddress.getPort()));
            }
            outputMessage.setURIScheme(message.getURIScheme());
            outputMessage.setPayload(new CoAPMessagePayload(blockwiseOutput.getNextPart(token, MAX_PAYLOAD_SIZE)));

            block.moreBlocks = blockwiseOutput.isDataAvailable(token);
            outputMessage.addOption(new CoAPMessageOption(isBlock1 ? CoAPMessageOptionCode.OptionBlock1 : CoAPMessageOptionCode.OptionBlock2, block.toInt()));

            outputMessage.setToken(message.getToken());

            client.send(outputMessage, null);
        } else {
            CoAPMessage mainMessage = blockwiseOutput.getMessage(token);
            message.setId(mainMessage.getId());
            blockwiseOutput.remove(token);
            return true;
        }
        return false;
    }

    private void saveMessage(CoAPMessage message, String token, boolean isBlock1) throws Throwable {
        CoAPMessage firstMessage;
        if (isBlock1) {
            firstMessage = message;
        } else {
            firstMessage = messagePool.get(message.getId());
            if (firstMessage == null) {
                ObservingResource resource = registryOfObservingResources.getResource(token);
                if (resource == null)
                    throw new Throwable("Какая-то ерунда пришла, шлем RST");

                firstMessage = resource.getInitiatingMessage();
                firstMessage.removeOption(CoAPMessageOptionCode.OptionObserve);
                firstMessage.removeOption(CoAPMessageOptionCode.OptionMaxAge);

                if (message.hasOption(CoAPMessageOptionCode.OptionObserve))
                    firstMessage.addOption(message.getOption(CoAPMessageOptionCode.OptionObserve));
                if (message.hasOption(CoAPMessageOptionCode.OptionMaxAge))
                    firstMessage.addOption(message.getOption(CoAPMessageOptionCode.OptionMaxAge));
            }
        }
        if (firstMessage != null) {
            blockwiseInput.startNewBlockwise(token, firstMessage);
        } else
            LogHelper.e("Message from pool for blockwise with id " + message.getId() + " is null");
    }

    private void addDataToMessageFromMain(CoAPMessage message, CoAPMessage mainMessage) {
        LogHelper.v("All block2 received, get handler by id " + mainMessage.getId());
        message.setId(mainMessage.getId()); // Ok, received message. Clear inner data, and send it to another layers!
        LogHelper.d("Received payload: " + mainMessage.getPayload().toString());

        LogHelper.d("main message uri = " + mainMessage.getURI());
        LogHelper.d("     message uri = " + message.getURI());

        message.setPayload(mainMessage.getPayload());

        for (CoAPMessageOption option : mainMessage.getOptions()) {
            if (option.code == CoAPMessageOptionCode.OptionURIQuery ||
                    option.code == CoAPMessageOptionCode.OptionURIHost ||
                    option.code == CoAPMessageOptionCode.OptionURIPath ||
                    option.code == CoAPMessageOptionCode.OptionURIScheme ||
                    option.code == CoAPMessageOptionCode.OptionURIPort)
                continue;

            CoAPMessageOption oldOption = message.getOption(option.code);
            if (oldOption == null) {
                LogHelper.v("Add option: " + option.code + ", value = " + option.value);
                message.addOption(option);
            } else {
                LogHelper.v("Set new option value: " + option.code + ", value = " + option.value);
                oldOption.value = option.value;
            }
        }
        if (mainMessage.getToken() != null) {
            LogHelper.v("Set token: " + Hex.encodeHexString(mainMessage.getToken()));
            message.setToken(mainMessage.getToken());
        }
        message.setURI(mainMessage.getURI());
    }

    @Override
    public boolean onSend(CoAPMessage message, Reference<InetSocketAddress> receiverAddress) {
        if (isBiggerThenCanBeTransferedBySingleBlock(message) &&
                !hasBlockOptions(message)) {
            String token = Hex.encodeHexString(message.getToken());
            blockwiseOutput.startNewBlockwise(token, message);

            byte[] nextPart = blockwiseOutput.getNextPart(token, BlockwiseLayer.MAX_PAYLOAD_SIZE);
            message.setPayload(new CoAPMessagePayload(nextPart));
            if (message.getCode().isRequest()) {
                message.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock1, new Block(0, true, BlockwiseLayer.MAX_PAYLOAD_SIZE).toInt()));
            } else {
                message.setPayload(new CoAPMessagePayload(nextPart));
                message.addOption(new CoAPMessageOption(CoAPMessageOptionCode.OptionBlock2, new Block(0, true, BlockwiseLayer.MAX_PAYLOAD_SIZE).toInt()));
            }
        }
        return true;
    }

    private boolean hasBlockOptions(CoAPMessage message) {
        return message.hasOption(CoAPMessageOptionCode.OptionBlock1) ||
                message.hasOption(CoAPMessageOptionCode.OptionBlock2);
    }

    private boolean isBiggerThenCanBeTransferedBySingleBlock(CoAPMessage message) {
        return message.getPayload() != null &&
                message.getPayload().content != null &&
                message.getPayload().content.length > MAX_PAYLOAD_SIZE;
    }
}
