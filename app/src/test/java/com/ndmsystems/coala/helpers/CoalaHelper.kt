package com.ndmsystems.coala.helpers

import com.ndmsystems.coala.ICoalaStorage

object CoalaHelper {
    val storage: ICoalaStorage by lazy {
        object : ICoalaStorage {
            val map: HashMap<String, Any> = hashMapOf()
            override fun put(key: String, obj: Any) {
                map[key] = obj
            }

            override fun <T> get(key: String, clz: Class<T>): T? {
                return map[key] as T?
            }

            override fun remove(key: String) {
                map.remove(key)
            }

        }
    }
}