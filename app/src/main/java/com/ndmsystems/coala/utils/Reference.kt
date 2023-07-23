package com.ndmsystems.coala.utils

/**
 * Created by Владимир on 28.06.2017.
 */
class Reference<T>(private var `object`: T) {
    fun set(`object`: T) {
        this.`object` = `object`
    }

    fun get(): T {
        return `object`
    }
}