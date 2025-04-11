package com.ndmsystems.coala.resource_discovery

import java.net.InetSocketAddress

/**
 * Created by bas on 19.09.16.
 */
class ResourceDiscoveryResult(payload: String, host: InetSocketAddress) {
    val payload: String
    val host: InetSocketAddress

    init {
        this.payload = payload
        this.host = host
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ResourceDiscoveryResult
        return if (payload != that.payload) false else host == that.host
    }

    override fun hashCode(): Int {
        var result = payload.hashCode()
        result = 31 * result + host.hashCode()
        return result
    }
}