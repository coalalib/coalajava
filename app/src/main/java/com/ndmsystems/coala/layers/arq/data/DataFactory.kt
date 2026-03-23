package com.ndmsystems.coala.layers.arq.data

object DataFactory {
    fun createEmpty(): IData {
        return InMemoryData(ByteArray(0))
    }

    fun create(bytes: ByteArray): IData {
        return InMemoryData(bytes)
    }
}