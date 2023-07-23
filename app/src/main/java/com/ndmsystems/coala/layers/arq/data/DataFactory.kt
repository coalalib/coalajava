package com.ndmsystems.coala.layers.arq.data

/**
 * Created by Владимир on 22.08.2017.
 */
object DataFactory {
    fun createEmpty(): IData {
        return InMemoryData(ByteArray(0))
    }

    fun create(bytes: ByteArray): IData {
        return InMemoryData(bytes)
    }
}