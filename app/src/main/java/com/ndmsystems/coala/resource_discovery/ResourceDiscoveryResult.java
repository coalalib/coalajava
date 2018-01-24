package com.ndmsystems.coala.resource_discovery;

import java.net.InetSocketAddress;
import java.util.Arrays;

/**
 * Created by bas on 19.09.16.
 */
public class ResourceDiscoveryResult {
    private final String[] resources;
    private final InetSocketAddress host;

    public ResourceDiscoveryResult(String[] resources, InetSocketAddress host) {
        this.resources = resources;
        this.host = host;
    }

    public String[] getResources() {
        return resources;
    }

    public InetSocketAddress getHost() {
        return host;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResourceDiscoveryResult that = (ResourceDiscoveryResult) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(resources, that.resources)) return false;
        return host.equals(that.host);

    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(resources);
        result = 31 * result + host.hashCode();
        return result;
    }
}
