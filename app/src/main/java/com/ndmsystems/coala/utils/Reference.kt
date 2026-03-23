package com.ndmsystems.coala.utils

class Reference<T>(private var `object`: T) {
    fun set(`object`: T) {
        this.`object` = `object`
    }

    fun get(): T {
        return `object`
    }
}