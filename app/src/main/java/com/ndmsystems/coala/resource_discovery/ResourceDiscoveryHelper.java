package com.ndmsystems.coala.resource_discovery;

import java.util.ArrayList;
import java.util.List;


public class ResourceDiscoveryHelper {
    private List<ResourceDiscoveryResult> resultsList = new ArrayList<>();

    public void clear() {
        resultsList.clear();
    }

    public List<ResourceDiscoveryResult> getResultsList() {
        return resultsList;
    }

    public void addResult(ResourceDiscoveryResult oneResource) {
        if(!resultsList.contains(oneResource)) {
            resultsList.add(oneResource);
        }
    }

    public String[] getResourcesFromMessage(String message) {
        return message.split(",");
    }
}
