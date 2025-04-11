package com.ndmsystems.coala.layers.arq

/**
 * Created by bas on 04.08.17.
 */
class Block private constructor(val number: Int, val isMoreComing: Boolean, private val blockSize: BlockSize, val data: ByteArray?) {

    constructor(number: Int, data: ByteArray?, isMoreComing: Boolean) : this(
        number, isMoreComing, BlockSize.getBlockSizeByDataBlock(
            data!!.size
        ), data
    )

    constructor(value: Int, data: ByteArray?) : this(value shr 0x4, value shr 0x3 and 0x1 == 1, BlockSize.values()[value and 7], data)

    fun toInt(): Int {
        val rawBlockNumber = number shl 4
        val rawMoreBlocks = (if (isMoreComing) 1 else 0) shl 3
        return rawBlockNumber or rawMoreBlocks or blockSize.ordinal
    }

    override fun toString(): String {
        return number.toString() + "|" + (if (isMoreComing) 1 else 0) + "|" + blockSize.value
    }

    enum class BlockSize {
        BLOCK_SIZE_16, BLOCK_SIZE_32, BLOCK_SIZE_64, BLOCK_SIZE_128, BLOCK_SIZE_256, BLOCK_SIZE_512, BLOCK_SIZE_1024;

        val value: Int
            get() = 1 shl ordinal + 4

        companion object {
            fun getBlockSizeByDataBlock(blockSize: Int): BlockSize {
                return if (blockSize >= 1024) {
                    BLOCK_SIZE_1024
                } else if (blockSize <= 16) {
                    BLOCK_SIZE_16
                } else {
                    val maxOneBit = Integer.highestOneBit(blockSize)
                    values()[Integer.numberOfTrailingZeros(maxOneBit) - 4]
                }
            }
        }
    }
}