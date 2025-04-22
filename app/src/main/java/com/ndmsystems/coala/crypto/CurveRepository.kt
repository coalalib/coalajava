package com.ndmsystems.coala.crypto

import com.ndmsystems.coala.ICoalaStorage

/**
 * Created by bas on 26.10.17.
 */
class CurveRepository(private val storage: ICoalaStorage) {
    val curve: Curve25519
        get() {
            val CURVE_KEY = "CURVE_KEY"
            var curve25519 = storage[CURVE_KEY, Curve25519::class.java]
            if (curve25519 == null) {
                curve25519 = Curve25519()
                storage.put(CURVE_KEY, curve25519)
            }
            return curve25519
        }
}