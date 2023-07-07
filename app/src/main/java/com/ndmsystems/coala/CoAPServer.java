package com.ndmsystems.coala;

import com.ndmsystems.coala.message.CoAPRequestMethod;
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryResult;

import java.util.List;

import io.reactivex.Single;

public interface CoAPServer {
    interface OnResourcesDiscovered {
        void onResourcesDiscovered(List<ResourceDiscoveryResult> resourceDiscoveryResults);
    }

    CoAPObservableResource getObservableResource(String path);

    void addObservableResource(String path, CoAPResource.CoAPResourceHandler handler);

    void addResource(String path, CoAPRequestMethod method, CoAPResource.CoAPResourceHandler handler);

    void removeResource(String path, CoAPRequestMethod method);

    Single<List<ResourceDiscoveryResult>> runResourceDiscovery();
}
