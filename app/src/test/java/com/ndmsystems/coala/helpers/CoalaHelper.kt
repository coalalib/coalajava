package com.ndmsystems.coala.helpers

import com.ndmsystems.coala.Coala
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

    // ConnectivityManager is only dereferenced on SDK_INT >= M; in JVM unit tests
    // SDK_INT is 0, so null is never actually dereferenced (the framework class
    // also can't be mocked by Mockito here — private constructor).
    fun coala(port: Int): Coala = Coala(port, storage, connectivityManager = null)
}