package com.ndmsystems.coala;

import com.ndmsystems.infrastructure.logging.LogHelper;
import com.ndmsystems.coala.message.CoAPRequestMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Владимир on 26.06.2017.
 */

public class ResourceRegistry {

    private Map<String, CoAPResourcesGroupForPath> resources = Collections.synchronizedMap(new HashMap<String, CoAPResourcesGroupForPath>());
    private CoAPClient client;

    public ResourceRegistry(CoAPClient client) {
        this.client = client;
    }

    public Map<String, CoAPResourcesGroupForPath> getResources() {
        return resources;
    }

    public CoAPObservableResource getObservableResource(String path) {
        LogHelper.v("searching for observable resource for path: " + path);
        CoAPResourcesGroupForPath resourcesGroupForPath = resources.get(path);
        if (resourcesGroupForPath != null) {
            for (CoAPResource resource : resourcesGroupForPath.getResources()) {
                LogHelper.v("resource path: " + resource.path + ", method: " + resource.method + ", is observable ? " + (resource instanceof CoAPObservableResource));
                if (resource instanceof CoAPObservableResource &&
                        resource.doesMatch(path, CoAPRequestMethod.GET)) {
                    return (CoAPObservableResource) resource;
                }
            }
        }
        return null;
    }

    public void addObservableResource(String path, CoAPResource.CoAPResourceHandler handler) {
        LogHelper.v("addObservableResource for path: " + path);
        CoAPResource resource = new CoAPObservableResource(CoAPRequestMethod.GET, path, handler, client);
        addResource(path, resource);
    }


    public void addResource(String path, CoAPRequestMethod method, CoAPResource.CoAPResourceHandler handler) {
        LogHelper.v("addResource for path: " + path);
        CoAPResource resource = new CoAPResource(method, path, handler);
        addResource(path, resource);
    }

    private void addResource(String path, CoAPResource resource) {
        CoAPResourcesGroupForPath resourcesGroupForPath = resources.get(path);
        if (resourcesGroupForPath == null) resourcesGroupForPath = new CoAPResourcesGroupForPath(path);

        resourcesGroupForPath.set(resource);
        resources.put(path, resourcesGroupForPath);
    }

    public void removeResource(String path, CoAPRequestMethod method) {
        LogHelper.v("removeResource for path: " + path);
        CoAPResourcesGroupForPath resourcesGroupForPath = resources.get(path);
        if (resourcesGroupForPath != null) resourcesGroupForPath.remove(method);
    }

    public CoAPResourcesGroupForPath getResourcesForPath(String path) {
        return resources.get(path);
    }
}
