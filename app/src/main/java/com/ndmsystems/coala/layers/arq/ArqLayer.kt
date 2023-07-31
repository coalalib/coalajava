package com.ndmsystems.coala.layers.arq

import com.ndmsystems.coala.BuildConfig
import com.ndmsystems.coala.CoAPClient
import com.ndmsystems.coala.CoAPHandler
import com.ndmsystems.coala.CoAPMessagePool
import com.ndmsystems.coala.LayersStack
import com.ndmsystems.coala.helpers.Hex.decodeHex
import com.ndmsystems.coala.helpers.Hex.encodeHexString
import com.ndmsystems.coala.helpers.MessageHelper.getMessageOptionsString
import com.ndmsystems.coala.helpers.logging.LogHelper.d
import com.ndmsystems.coala.helpers.logging.LogHelper.e
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.layers.ReceiveLayer
import com.ndmsystems.coala.layers.SendLayer
import com.ndmsystems.coala.layers.arq.data.DataFactory
import com.ndmsystems.coala.layers.arq.states.LoggableState
import com.ndmsystems.coala.layers.arq.states.ReceiveState
import com.ndmsystems.coala.layers.arq.states.SendState
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessageOption
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import com.ndmsystems.coala.message.CoAPMessagePayload
import com.ndmsystems.coala.message.CoAPMessageType
import com.ndmsystems.coala.utils.Reference
import net.jodah.expiringmap.ExpirationPolicy
import net.jodah.expiringmap.ExpiringMap
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Created by bas on 02.08.17.
 */
class ArqLayer(
    private val client: CoAPClient,
    private val messagePool: CoAPMessagePool
) : ReceiveLayer, SendLayer {
    private val receiveStates: MutableMap<String, ReceiveState> = ExpiringMap.builder()
        .expirationPolicy(ExpirationPolicy.ACCESSED)
        .expiration(10, TimeUnit.SECONDS)
        .build()
    private val sendStates: MutableMap<String, SendState> = ConcurrentHashMap()
    private fun isBlockedMessage(message: CoAPMessage): Boolean {
        return message.hasOption(CoAPMessageOptionCode.OptionBlock1) ||
                message.hasOption(CoAPMessageOptionCode.OptionBlock2)
    }

    private fun isAboutArq(message: CoAPMessage): Boolean {
        return message.hasOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize)
    }

    private fun getBlock1(message: CoAPMessage): Block {
        val data = message.payload?.content
        return Block(
            message.getOption(CoAPMessageOptionCode.OptionBlock1)?.value as Int,
            data
        )
    }

    private fun getBlock2(message: CoAPMessage): Block {
        val data = message.payload?.content
        return Block(
            message.getOption(CoAPMessageOptionCode.OptionBlock2)?.value as Int,
            data
        )
    }

    override fun onReceive(message: CoAPMessage, senderAddressReference: Reference<InetSocketAddress>): LayersStack.LayerResult {
        if (!isAboutArq(message)) {
            return LayersStack.LayerResult(true)
        }
        if (message.code == CoAPMessageCode.CoapCodeEmpty && message.type == CoAPMessageType.ACK && !message.hasOption(CoAPMessageOptionCode.OptionBlock1)) { //For block1 block2 mixed mode, message ending block1 start block2, like Received data from Peer, id 51486, payload:'', address: 138.197.191.160:5683 type: ACK code: CoapCodeEmpty path: null schema: coap token 951e67cdc72af162
            //Options: OptionBlock1 : 'com.ndmsystems.coala.layers.arq.Block@a046fab'(8174) OptionSelectiveRepeatWindowSize : '70', address: 138.197.191.160:5683 type: CON code: CoapCodeContent path: null schema: coap token 951e67cdc72af162
            //Options: OptionBlock2 : 'com.ndmsystems.coala.layers.arq.Block@72bd608'(30) OptionSelectiveRepeatWindowSize : '300'
            messagePool.setNoNeededSending(message)
            return LayersStack.LayerResult(false)
        }
        if (!isBlockedMessage(message)) {
            return LayersStack.LayerResult(true)
        }
        if (message.token == null) {
            sendResetMessage(message, senderAddressReference.get())
        }
        val windowSize = message.getOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize)!!.value as Int
        val ackMessage = CoAPMessage.ackTo(message, senderAddressReference.get(), CoAPMessageCode.CoapCodeEmpty)
        if (message.hasOption(CoAPMessageOptionCode.OptionBlock1)) {
            val block = getBlock1(message)
            return process(message, CoAPMessageOptionCode.OptionBlock1, block, windowSize, ackMessage)
        }
        if (message.hasOption(CoAPMessageOptionCode.OptionBlock2)) {
            val block = getBlock2(message)
            return process(message, CoAPMessageOptionCode.OptionBlock2, block, windowSize, ackMessage)
        }
        return LayersStack.LayerResult(true)
    }

    private fun sendResetMessage(incomingMessage: CoAPMessage, fromAddress: InetSocketAddress) {
        val resetMessage = CoAPMessage.resetTo(incomingMessage, fromAddress)
        client.send(resetMessage, null)
    }

    private fun process(
        incomingMessage: CoAPMessage,
        blockOptionCode: CoAPMessageOptionCode,
        block: Block,
        windowSize: Int,
        ackMessage: CoAPMessage
    ): LayersStack.LayerResult {
        var mutableIncomingMessage: CoAPMessage = incomingMessage
        val token = encodeHexString(mutableIncomingMessage.token)
        when (mutableIncomingMessage.type) {
            CoAPMessageType.ACK, CoAPMessageType.RST -> {
                // Transmit ACK
                didTransmit(block.number, token)
                messagePool.remove(mutableIncomingMessage)
                val sendState = sendStates[token]
                if (sendState != null && sendState.isCompleted) {
                    v("ARQ: Sending completed, pushing to message pool original message" + sendState.originalMessage.id)
                    val originalMessage = sendState.originalMessage
                    return if (mutableIncomingMessage.code == CoAPMessageCode.CoapCodeEmpty
                        && mutableIncomingMessage.type == CoAPMessageType.ACK
                    ) {
                        messagePool.add(originalMessage)
                        messagePool.setNoNeededSending(originalMessage)
                        sendStates.remove(token)
                        LayersStack.LayerResult(false)
                    } else {
                        mutableIncomingMessage = originalMessage
                        sendStates.remove(token)
                        LayersStack.LayerResult(true, mutableIncomingMessage)
                    }
                }
            }

            CoAPMessageType.CON -> {
                // Receive CON
                val payload = mutableIncomingMessage.payload
                if (payload == null) {
                    e("ARQ: payload expected for token = $token")
                    throw RuntimeException("ARQ: payload expected for token = $token")
                }
                var receiveState = receiveStates[token]
                if (receiveState == null) {
                    v("ARQ: creating ReceiveState for token = $token")
                    receiveState = ReceiveState(mutableIncomingMessage)
                    receiveStates[token] = receiveState
                }
                receiveState.didReceiveBlock(block, mutableIncomingMessage.code)
                ackMessage.addOption(CoAPMessageOption(blockOptionCode, block.toInt()))
                ackMessage.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, windowSize))
                if (receiveState.isTransferCompleted) {
                    //receiveStates.remove(token);
                    val originalMessage = messagePool.getSourceMessageByToken(mutableIncomingMessage.hexToken)
                    if (originalMessage != null) {
                        v(
                            "ARQ: Receive " + mutableIncomingMessage.hexToken + " completed, size " + receiveState.dataSize
                                    + ", passing message " + originalMessage.id + ", with token: " + encodeHexString(originalMessage.token)
                                    + " along"
                        )
                        if (originalMessage.hasOption(CoAPMessageOptionCode.OptionProxyURI)) {
                            ackMessage.addOption(originalMessage.getOption(CoAPMessageOptionCode.OptionProxyURI)!!)
                        }
                        originalMessage.proxy?.let {
                            ackMessage.setProxy(it)
                            originalMessage.getProxySecurityId()?.let {
                                ackMessage.setProxySecurityId(it)
                            }
                        }
                        mutableIncomingMessage.id = originalMessage.id
                    } else {
                        v(
                            "ARQ: Receive with originalMessage = null, token " + mutableIncomingMessage.hexToken + " completed, size "
                                    + receiveState.dataSize
                        )
                    }
                    ackMessage.code = CoAPMessageCode.CoapCodeEmpty
                    client.send(ackMessage, null)

                    //mutableIncomingMessage = messagePool.getSourceMessageByToken(mutableIncomingMessage.getHexToken());
                    mutableIncomingMessage.payload = CoAPMessagePayload(receiveState.data ?: ByteArray(0))
                    mutableIncomingMessage.code = CoAPMessageCode.CoapCodeContent
                    mutableIncomingMessage.type = CoAPMessageType.ACK
                    return LayersStack.LayerResult(true, mutableIncomingMessage)
                } else {
                    if (BuildConfig.DEBUG) { //For no slowing prod version, so many logs, what don't showing anymore
                        v(
                            "ARQ: Receive " + mutableIncomingMessage.hexToken + " in progress, responding with ACK continued, received: "
                                    + receiveState.dataSize
                        )
                    }
                    ackMessage.code = CoAPMessageCode.CoapCodeContinue
                    val originalMessage = messagePool.getSourceMessageByToken(mutableIncomingMessage.hexToken)
                    if (originalMessage != null) {
                        if (originalMessage.hasOption(CoAPMessageOptionCode.OptionProxyURI)) {
                            ackMessage.addOption(originalMessage.getOption(CoAPMessageOptionCode.OptionProxyURI)!!)
                        }
                        originalMessage.proxy?.let { ackMessage.setProxy(it) }
                    }
                    client.send(ackMessage, null)
                }
            }

            CoAPMessageType.NON -> {
                e("ARQ: NON message received")
                throw RuntimeException("ARQ: NON message received")
            }
        }
        mutableIncomingMessage.removeOption(blockOptionCode)
        return LayersStack.LayerResult(false, mutableIncomingMessage)
    }

    private fun didTransmit(blockNumber: Int, token: String) {
        v("ARQ: did transmit block = $blockNumber for token = $token")
        val sendState = sendStates[token]
        if (sendState != null) {
            sendState.didTransmit(blockNumber)
            sendMoreData(token)
        }
    }

    private fun sendMoreData(token: String) {
        val state = sendStates[token]
        var block: Block?
        if (state != null) {
            while (state.popBlock().also { block = it } != null) {
                v("ARQ: did pop block number = " + block!!.number)
                send(block!!, state.originalMessage, token, state)
                state.incrementNumberOfMessage()
                sendStates[token] = state
            }
        }
    }

    private fun send(block: Block, originalMessage: CoAPMessage?, token: String, state: SendState) {
        val blockMessage = CoAPMessage(originalMessage!!.type, originalMessage.code)
        blockMessage.setOptions(originalMessage.getOptions())
        val blockCode = if (originalMessage.isRequest) CoAPMessageOptionCode.OptionBlock1 else CoAPMessageOptionCode.OptionBlock2
        val blockOption = CoAPMessageOption(blockCode, block.toInt())
        blockMessage.addOption(blockOption)
        val srOption = CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, state.windowSize)
        blockMessage.addOption(srOption)
        blockMessage.token = decodeHex(token.toCharArray())
        blockMessage.payload = CoAPMessagePayload(block.data ?: ByteArray(0))
        blockMessage.setURI(originalMessage.getURI())
        if (originalMessage.hasOption(CoAPMessageOptionCode.OptionProxyURI)) {
            blockMessage.addOption(originalMessage.getOption(CoAPMessageOptionCode.OptionProxyURI)!!)
        }
        originalMessage.proxy?.let { blockMessage.setProxy(it) }
        blockMessage.resendHandler = state
        client.send(blockMessage, object : CoAPHandler {
            override fun onMessage(message: CoAPMessage, error: String?) {
                if (error != null) {
                    v("Block number = " + block.number + " failed " + error)
                    fail(token)
                    return
                }
                v("Block number = " + block.number + " sent")
            }

            override fun onAckError(error: String) {
                v("Block number = " + block.number + " failed")
                fail(token)
            }
        })
    }

    private fun fail(token: String) {
        v("ARQ: fail to transfer for token = $token")
        val sendState = sendStates[token]
        if (sendState != null) {
            sendState.onError(client.getMessageDeliveryInfo(sendState.originalMessage))
            sendStates.remove(token)
        }
        receiveStates.remove(token)
    }

    override fun onSend(message: CoAPMessage, receiverAddressReference: Reference<InetSocketAddress>): LayersStack.LayerResult {
        if (hasWindowSizeOption(message) && !isStartMixingModeMessage(message)) {
            return LayersStack.LayerResult(true)
        }
        if (!isBiggerThenCanBeTransferedBySingleBlock(message) || message.token == null) {
            return LayersStack.LayerResult(true)
        }
        val token = encodeHexString(message.token)
        val payload = message.payload
        v("ARQ: removing original message " + message.id + " from pool")
        messagePool.remove(message)
        var originalMessage: CoAPMessage? = null
        var ackMessage: CoAPMessage? = null
        when (message.type) {
            CoAPMessageType.ACK, CoAPMessageType.RST -> {
                originalMessage = CoAPMessage(message)
                originalMessage.type = CoAPMessageType.CON
                originalMessage.removeOption(CoAPMessageOptionCode.OptionBlock1)
                CoAPMessage.convertToEmptyAck(message, receiverAddressReference.get())
                ackMessage = message
                ackMessage.addOption(CoAPMessageOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize, WINDOW_SIZE))
                d(
                    "ARQ: Send empty ack, id " + ackMessage.id + " " +
                            "payload: '" + ackMessage + " " +
                            "destination host: " + ackMessage.getURI() + " " +
                            "type: " + ackMessage.type + " " +
                            "code: " + ackMessage.code.name + " " +
                            "token: " + encodeHexString(ackMessage.token) + " " +
                            "options: " + getMessageOptionsString(ackMessage)
                )
            }

            CoAPMessageType.NON -> {
                originalMessage = CoAPMessage(message)
                originalMessage.type = CoAPMessageType.CON
                originalMessage.removeOption(CoAPMessageOptionCode.OptionBlock1)
            }

            CoAPMessageType.CON -> originalMessage = CoAPMessage(message)
        }
        val sendState = SendState(DataFactory.create(payload?.content ?: ByteArray(0)), WINDOW_SIZE, MAX_PAYLOAD_SIZE, originalMessage)
        sendStates[token] = sendState
        sendMoreData(token)
        v("ARQ: split message " + message.id + " to values. Sending payload = " + payload?.content?.size)
        return LayersStack.LayerResult(ackMessage != null)
    }

    private fun isStartMixingModeMessage(message: CoAPMessage): Boolean {
        return (!message.code.isRequest
                && message.hasOption(CoAPMessageOptionCode.OptionBlock1)
                && !message.hasOption(CoAPMessageOptionCode.OptionBlock2)
                && isBiggerThenCanBeTransferedBySingleBlock(message))
    }

    private fun hasWindowSizeOption(message: CoAPMessage): Boolean {
        return message.hasOption(CoAPMessageOptionCode.OptionSelectiveRepeatWindowSize)
    }

    private fun isBiggerThenCanBeTransferedBySingleBlock(message: CoAPMessage): Boolean {
        return message.payload != null && message.payload!!.content.size > MAX_PAYLOAD_SIZE
    }

    fun getArqReceivingStateForToken(token: ByteArray?): LoggableState? {
        return receiveStates[encodeHexString(token)]
    }

    companion object {
        private const val WINDOW_SIZE = 70
        private const val MAX_PAYLOAD_SIZE = 1024
    }
}