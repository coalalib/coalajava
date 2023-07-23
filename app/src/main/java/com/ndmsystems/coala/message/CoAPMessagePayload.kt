package com.ndmsystems.coala.message

class CoAPMessagePayload {
    @JvmField
    var content: ByteArray

    constructor(content: ByteArray) {
        this.content = content
    }

    constructor(stringContent: String) {
        content = stringContent.toByteArray()
    }

    override fun toString(): String {
        return if (content.isEmpty()) "" else String(content)
    }
}