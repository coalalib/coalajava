package com.ndmsystems.coala.crypto;

import com.ndmsystems.coala.ICoalaStorage;

/**
 * Created by bas on 26.10.17.
 */

public class CurveRepository {
    private final ICoalaStorage storage;
    private final String CURVE_KEY = "CURVE_KEY";

    public CurveRepository(ICoalaStorage storage) {
        this.storage = storage;
    }

    public Curve25519 getCurve() {
        Curve25519 curve25519 = storage.get(CURVE_KEY, Curve25519.class);
        if (curve25519 == null) {
            curve25519 = new Curve25519();
            storage.put(CURVE_KEY, curve25519);
        }
        return curve25519;
    }
}
