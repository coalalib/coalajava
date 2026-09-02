package com.ndmsystems.coala

import com.ndmsystems.coala.CoAPResource.CoAPResourceHandler
import com.ndmsystems.coala.message.CoAPRequestMethod
import com.ndmsystems.coala.resource_discovery.ResourceDiscoveryResult

interface CoAPServer {

    fun getObservableResource(path: String): CoAPObservableResource?
    fun addObservableResource(path: String, handler: CoAPResourceHandler)
    fun addResource(path: String, method: CoAPRequestMethod, handler: CoAPResourceHandler)
    fun removeResource(path: String, method: CoAPRequestMethod)
    suspend fun runResourceDiscovery(): List<ResourceDiscoveryResult>
}