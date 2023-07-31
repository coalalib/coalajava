package com.ndmsystems.coala

import com.ndmsystems.coala.CoAPResource.CoAPResourceHandler
import com.ndmsystems.coala.helpers.logging.LogHelper.v
import com.ndmsystems.coala.message.CoAPRequestMethod
import java.util.Collections

/**
 * Created by Владимир on 26.06.2017.
 */
class ResourceRegistry(private val client: CoAPClient) {
    private val resources = Collections.synchronizedMap(HashMap<String, CoAPResourcesGroupForPath>())
    fun getResources(): Map<String, CoAPResourcesGroupForPath> {
        return resources
    }

    fun getObservableResource(path: String): CoAPObservableResource? {
        v("searching for observable resource for path: $path")
        val resourcesGroupForPath = resources[path]
        if (resourcesGroupForPath != null) {
            for (resource in resourcesGroupForPath.resources) {
                v("resource path: " + resource.path + ", method: " + resource.method + ", is observable ? " + (resource is CoAPObservableResource))
                if (resource is CoAPObservableResource &&
                    resource.doesMatch(path, CoAPRequestMethod.GET)
                ) {
                    return resource
                }
            }
        }
        return null
    }

    fun addObservableResource(path: String, handler: CoAPResourceHandler) {
        v("addObservableResource for path: $path")
        val resource: CoAPResource = CoAPObservableResource(CoAPRequestMethod.GET, path, handler, client)
        addResource(path, resource)
    }

    fun addResource(path: String, method: CoAPRequestMethod?, handler: CoAPResourceHandler?) {
        v("addResource for path: $path")
        val resource = CoAPResource(method!!, path, handler!!)
        addResource(path, resource)
    }

    private fun addResource(path: String, resource: CoAPResource) {
        var resourcesGroupForPath = resources[path]
        if (resourcesGroupForPath == null) resourcesGroupForPath = CoAPResourcesGroupForPath(path)
        resourcesGroupForPath.set(resource)
        resources[path] = resourcesGroupForPath
    }

    fun removeResource(path: String, method: CoAPRequestMethod?) {
        v("removeResource for path: $path")
        val resourcesGroupForPath = resources[path]
        resourcesGroupForPath?.remove(method)
    }

    fun getResourcesForPath(path: String): CoAPResourcesGroupForPath? {
        return resources[path]
    }
}