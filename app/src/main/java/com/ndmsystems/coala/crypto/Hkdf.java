/**
 * Copyright (C) 2013-2016 decrypt Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package com.ndmsystems.coala.crypto;

import com.ndmsystems.coala.helpers.Hex;
import com.ndmsystems.infrastructure.logging.LogHelper;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Hkdf {

    private static final int HASH_OUTPUT_SIZE  = 32;
    private static final int OUTPUT_LENGTH  = 40;
    private static final int KEY_LEN  = 16;

    public final byte[] peerKey = new byte[KEY_LEN];
    public final byte[] myKey = new byte[KEY_LEN];
    public final byte[] peerIV = new byte[4];
    public final byte[] myIV = new byte[4];

    public Hkdf(byte[] sharedSecret, byte[] salt, byte[] info) {
        byte[] okm = deriveSecrets(sharedSecret, salt, info);
        LogHelper.v("OKM: " + Hex.encodeHexString(okm));
        System.arraycopy(okm, 0, peerKey, 0, KEY_LEN);
        System.arraycopy(okm, KEY_LEN, myKey, 0, KEY_LEN);
        System.arraycopy(okm, 2 * KEY_LEN, peerIV, 0, 4);
        System.arraycopy(okm, 2 * KEY_LEN + 4, myIV, 0, 4);
    }

    public String toString() {
        return Hex.encodeHexString(peerKey) + Hex.encodeHexString(myKey) + Hex.encodeHexString(peerIV) + Hex.encodeHexString(myIV);
    }

    private byte[] deriveSecrets(byte[] inputKeyMaterial, byte[] salt, byte[] info) {
        if (salt == null || salt.length == 0) salt = new byte[HASH_OUTPUT_SIZE];
        byte[] prk = extract(salt, inputKeyMaterial);
        return expand(prk, info, OUTPUT_LENGTH);
    }

    private byte[] extract(byte[] salt, byte[] inputKeyMaterial) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            return mac.doFinal(inputKeyMaterial);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    private byte[] expand(byte[] prk, byte[] info, int outputSize) {
        try {
            int                   iterations     = (int) Math.ceil((double) outputSize / (double) HASH_OUTPUT_SIZE);
            byte[]                mixin          = new byte[0];
            ByteArrayOutputStream results        = new ByteArrayOutputStream();
            int                   remainingBytes = outputSize;

            for (int i= 1;i<iterations + 1;i++) {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(prk, "HmacSHA256"));

                mac.update(mixin);
                if (info != null) {
                    mac.update(info);
                }
                mac.update((byte)i);

                byte[] stepResult = mac.doFinal();
                int    stepSize   = Math.min(remainingBytes, stepResult.length);

                results.write(stepResult, 0, stepSize);

                mixin          = stepResult;
                remainingBytes -= stepSize;
            }

            return results.toByteArray();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }
}
