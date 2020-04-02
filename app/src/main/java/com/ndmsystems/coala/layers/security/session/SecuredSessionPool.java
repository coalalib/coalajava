package com.ndmsystems.coala.layers.security.session;


import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

public class SecuredSessionPool {
    private ConcurrentLinkedHashMap<String, SecuredSession> pool;

    public SecuredSessionPool() {
        pool = new ConcurrentLinkedHashMap.Builder<String, SecuredSession>().maximumWeightedCapacity(500).build();
    }


    public void set(String hash, SecuredSession session) {
        if (hash != null) {
            pool.put(hash, session);
        }
    }

    public SecuredSession get(String hash) {
        if (hash == null) {
            return null;
        }

        return pool.get(hash);
    }

    public SecuredSession getByPeerProxySecurityId(Long peerProxySecurityId) {
        if (peerProxySecurityId == null) {
            return null;
        }

        for (SecuredSession session : pool.values()) {
            if(session != null && peerProxySecurityId.equals(session.getPeerProxySecurityId())){
                return session;
            }
        }

        return null;
    }

    public void remove(String hash) {
        pool.remove(hash);
    }

    public void clear() {
        pool.clear();
    }
}
