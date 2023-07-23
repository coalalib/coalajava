package com.ndmsystems.coala.layers.arq.data

/**
 * Created by Владимир on 16.08.2017.
 */
class InMemoryData(private var bytes: ByteArray) : IData {
    override fun get(from: Int, to: Int): ByteArray? {
        return bytes.copyOfRange(from, to)
    }

    override fun get(): ByteArray {
        return bytes
    }

    override fun append(data: IData) {
        val oldBytes = bytes
        bytes = ByteArray(bytes.size + data.size())
        System.arraycopy(oldBytes, 0, bytes, 0, oldBytes.size)
        System.arraycopy(data.get(), 0, bytes, oldBytes.size, data.size())
    }

    override fun size(): Int {
        return bytes.size
    }
}