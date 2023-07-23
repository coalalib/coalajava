package com.ndmsystems.coala.layers.arq.states

import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.layers.arq.Block
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode

/**
 * Created by Владимир on 16.08.2017.
 */
class ReceiveState(val initiatingMessage: CoAPMessage?) : LoggableState() {
    private val accumulator = HashMap<Int, ByteArray?>()
    private var lastBlockNumber = Int.MAX_VALUE
    private var numberOfReceivedBlocks = 0
    val data: ByteArray?
        get() {
            if (lastBlockNumber == Int.MAX_VALUE) {
                return null
            }
            val result = ByteArray(dataSize)
            var currentPosInResult = 0
            for (i in 0..lastBlockNumber) {
                if (accumulator.containsKey(i) && accumulator[i] != null) {
                    val forCopy = accumulator[i]
                    if (forCopy != null) {
                        System.arraycopy(forCopy, 0, result, currentPosInResult, forCopy.size)
                        currentPosInResult += forCopy.size
                    }
                } else {
                    v("Accumulator don't contain block number $i or it's null")
                }
            }
            return result
        }
    val isTransferCompleted: Boolean
        get() = numberOfReceivedBlocks > lastBlockNumber

    fun didReceiveBlock(block: Block, code: CoAPMessageCode) {
        if (code != CoAPMessageCode.CoapCodeContinue) initiatingMessage!!.code = code
        if (accumulator.containsKey(block.number)) {
            v("Already received block with number " + block.number)
            onResend()
        } else {
            numberOfReceivedBlocks++
            accumulator[block.number] = block.data
            if (!block.isMoreComing) {
                lastBlockNumber = block.number
                v("Received last block, lastBlockNumber = $lastBlockNumber")
            }
            if (isTransferCompleted) onTransferCompleted()
        }
    }

    override val dataSize: Int
        get() {
            var sumSize = 0
            for (bytes in accumulator.values) {
                sumSize += bytes!!.size
            }
            return sumSize
        }
    override val token: ByteArray?
        get() = initiatingMessage!!.token
    override val isIncoming: Boolean
        get() = true
}