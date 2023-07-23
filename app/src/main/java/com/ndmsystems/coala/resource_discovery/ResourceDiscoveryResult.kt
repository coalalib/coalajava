package com.ndmsystems.coala.resource_discovery;

import java.net.InetSocketAddress;

/**
 * Created by bas on 19.09.16.
 */
public class ResourceDiscoveryResult {
    private final String payload;
    private final InetSocketAddress host;

    public ResourceDiscoveryResult(String payload, InetSocketAddress host) {
        this.payload = payload == null ? "" : payload;
        this.host = host;
    }

    public InetSocketAddress getHost() {
        return host;
    }

    public String getPayload() {
        return payload;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResourceDiscoveryResult that = (ResourceDiscoveryResult) o;

        if (!payload.equals(that.payload)) return false;
        return host.equals(that.host);

    }

    @Override
    public int hashCode() {
        int result = payload.hashCode();
        result = 31 * result + host.hashCode();
        return result;
    }
}
