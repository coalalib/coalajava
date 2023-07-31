package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPRequestMethod

open class CoAPResource(val method: CoAPRequestMethod, val path: String, val handler: CoAPResourceHandler) {

    fun doesMatch(path: String): Boolean {
        return this.path == path
    }

    fun doesMatch(path: String, method: CoAPRequestMethod): Boolean {
        return this.path == path && this.method == method
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other === this) return true
        if (other !is CoAPResource) return false
        return method === other.method && path == other.path
    }

    abstract class CoAPResourceHandler {
        abstract fun onReceive(inputData: CoAPResourceInput): CoAPResourceOutput
    }
}