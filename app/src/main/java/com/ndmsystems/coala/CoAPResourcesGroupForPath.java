package com.ndmsystems.coala;

import com.ndmsystems.coala.message.CoAPRequestMethod;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CoAPResourcesGroupForPath {
    private final String path;
    private final Map<CoAPRequestMethod, CoAPResource> coAPResourceHashMap;

    public CoAPResourcesGroupForPath(String path) {
        this.path = path;
        coAPResourceHashMap = Collections.synchronizedMap(new HashMap<CoAPRequestMethod, CoAPResource>());
    }

    public Collection<CoAPResource> getResources() {
        return coAPResourceHashMap.values();
    }

    public CoAPResource getResourceByMethod(CoAPRequestMethod method) {
        return coAPResourceHashMap.get(method);
    }

    public void set(CoAPResource resource) {
        coAPResourceHashMap.put(resource.method, resource);
    }

    public void remove(CoAPRequestMethod method) {
        coAPResourceHashMap.remove(method);
    }

    public String getPath() {
        return path;
    }
}
