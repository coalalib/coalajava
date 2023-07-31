package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPRequestMethod
import java.util.Collections

class CoAPResourcesGroupForPath(val path: String) {
    private val coAPResourceHashMap: MutableMap<CoAPRequestMethod?, CoAPResource> = Collections.synchronizedMap(HashMap())

    val resources: Collection<CoAPResource>
        get() = coAPResourceHashMap.values

    fun getResourceByMethod(method: CoAPRequestMethod?): CoAPResource? {
        return coAPResourceHashMap[method]
    }

    fun set(resource: CoAPResource) {
        coAPResourceHashMap[resource.method] = resource
    }

    fun remove(method: CoAPRequestMethod?) {
        coAPResourceHashMap.remove(method)
    }
}