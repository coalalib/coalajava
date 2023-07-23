package com.ndmsystems.coala.layers.security.session

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap

class SecuredSessionPool {
    private val pool: ConcurrentLinkedHashMap<String, SecuredSession> =
        ConcurrentLinkedHashMap.Builder<String, SecuredSession>().maximumWeightedCapacity(500).build()

    operator fun set(hash: String?, session: SecuredSession) {
        if (hash != null) {
            pool[hash] = session
        }
    }

    operator fun get(hash: String?): SecuredSession? {
        return if (hash == null) {
            null
        } else pool[hash]
    }

    fun getByPeerProxySecurityId(peerProxySecurityId: Long?): SecuredSession? {
        if (peerProxySecurityId == null) {
            return null
        }
        for (session in pool.values) {
            if (session != null && peerProxySecurityId == session.peerProxySecurityId) {
                return session
            }
        }
        return null
    }

    fun remove(hash: String) {
        pool.remove(hash)
    }

    fun clear() {
        pool.clear()
    }
}