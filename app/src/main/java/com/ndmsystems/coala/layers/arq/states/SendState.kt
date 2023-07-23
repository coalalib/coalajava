package com.ndmsystems.coala.layers.arq.states

import com.ndmsystems.coala.MessageDeliveryInfo
import com.ndmsystems.coala.exceptions.BaseCoalaThrowable
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.helpers.logging.LogHelper.w
import com.ndmsystems.coala.layers.arq.Block
import com.ndmsystems.coala.layers.arq.SlidingWindow
import com.ndmsystems.coala.layers.arq.data.IData
import com.ndmsystems.coala.message.CoAPMessage
import kotlin.math.min

/**
 * Created by Владимир on 16.08.2017.
 */
class SendState(data: IData?, windowSize: Int, blockSize: Int, originalMessage: CoAPMessage) : LoggableState() {
    private val blockSize: Int
    private val data: IData?
    private val window: SlidingWindow<Boolean>
    val originalMessage: CoAPMessage

    init {
        val mutableWindowSize: Int
        this.data = data
        this.blockSize = blockSize
        this.originalMessage = originalMessage
        val totalBlocks = data!!.size() / blockSize + if (data.size() % blockSize != 0) 1 else 0
        mutableWindowSize = min(windowSize, totalBlocks)
        window = SlidingWindow(mutableWindowSize, -mutableWindowSize)
        if (mutableWindowSize > 0) {
            for (i in -mutableWindowSize..-1) window[i] = true
        }
    }

    val windowSize: Int
        get() = window.getSize()

    fun popBlock(): Block? {
        if (window.advance() == null) {
            v("ARQ: popBlock() window.advance() == nil, no more blocks yet")
            return null
        }
        val blockNumber = window.tail()
        val rangeStart = blockNumber * blockSize
        val rangeEnd = Math.min(rangeStart + blockSize, data!!.size())
        if (rangeStart >= rangeEnd) {
            v("ARQ: popBlock() rangeStart $rangeStart > rangeEnd $rangeEnd")
            return null
        }
        if (blockNumber == -1) {
            w("BlockNumber = -1 oO, ")
        }
        return Block(blockNumber, data[rangeStart, rangeEnd], rangeEnd != data.size())
    }

    fun onError(messageDeliveryInfo: MessageDeliveryInfo?) {
        originalMessage.responseHandler.onError(BaseCoalaThrowable("ARQ: fail to transfer").setMessageDeliveryInfo(messageDeliveryInfo))
    }

    fun didTransmit(blockNumber: Int) {
        window[blockNumber] = true
        if (isCompleted) onTransferCompleted()
    }

    val isCompleted: Boolean
        get() {
            var lastDeliveredBlock = window.offset
            var index = 0
            while (index < window.getSize() && window.getValue(index) != null && window.getValue(index)!!) {
                lastDeliveredBlock += 1
                index += 1
            }
            return lastDeliveredBlock * blockSize >= data!!.size()
        }
    override val dataSize: Int
        get() = data!!.size()
    override val token: ByteArray?
        get() = originalMessage.token
    override val isIncoming: Boolean
        get() = false
}