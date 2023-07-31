package com.ndmsystems.coala

interface ICoalaStorage {
    fun put(key: String, obj: Any)
    operator fun <T> get(key: String, clz: Class<T>): T?
    fun remove(key: String)
}