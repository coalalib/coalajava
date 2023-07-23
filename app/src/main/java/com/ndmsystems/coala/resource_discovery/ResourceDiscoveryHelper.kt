package com.ndmsystems.coala.resource_discovery

class ResourceDiscoveryHelper {
    val resultsList: MutableList<ResourceDiscoveryResult> = ArrayList()
    fun clear() {
        resultsList.clear()
    }

    fun addResult(oneResource: ResourceDiscoveryResult) {
        if (!resultsList.contains(oneResource)) {
            resultsList.add(oneResource)
        }
    }
}