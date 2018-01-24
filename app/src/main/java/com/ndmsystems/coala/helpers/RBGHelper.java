package com.ndmsystems.coala.helpers;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Random Bytes Generator
 */
public class RBGHelper {
    public static byte[] rbg(int length) {
        byte[] random = new byte[length];

        try {
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.nextBytes(random);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }

        return random;
    }
}
