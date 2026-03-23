package com.ndmsystems.coala.layers.arq.data

/**
 * Abstraction so the payload access implementation can be swapped.
 * One option is keeping data in RAM, which is unacceptable for very large payloads (e.g. video).
 * This interface can later back a more memory-efficient implementation.
 */
interface IData {
    /**
     * @param from initial index of range - inclusive
     * @param to   final index of range - exclusive
     * @return requested bytes
     */
    operator fun get(from: Int, to: Int): ByteArray?

    /**
     * @return all bytes
     */
    fun get(): ByteArray
    fun append(data: IData)
    fun size(): Int
}