package com.ndmsystems.coala.message

import java.nio.charset.StandardCharsets

class CoAPMessagePayload {
    @JvmField
    var content: ByteArray

    constructor(content: ByteArray) {
        this.content = content
    }

    constructor(stringContent: String) {
        content = stringContent.toByteArray(StandardCharsets.UTF_8)
    }

    override fun toString(): String {
        return if (content.isEmpty()) "" else String(content, StandardCharsets.UTF_8)
    }
}
