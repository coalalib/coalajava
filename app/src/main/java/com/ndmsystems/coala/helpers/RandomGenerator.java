package com.ndmsystems.coala.helpers;

import com.ndmsystems.infrastructure.logging.LogHelper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Created by bas on 15.11.16.
 */

public class RandomGenerator {

    private static Random random = new Random();

    public static byte[] getRandom(int size) {
        byte[] b = new byte[size];
        random.nextBytes(b);
        return b;
    }

    public static Integer getRandomInt() {
        return random.nextInt();
    }

    private static byte[] sha(final byte[] uri) {
        MessageDigest digest=null;
        String hash;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            digest.update(uri);

            return digest.digest();

        } catch (NoSuchAlgorithmException e1) {
            LogHelper.e("NoSuchAlgorithmException, " + e1.getMessage());
            e1.printStackTrace();
            return new byte[]{};
        }
    }
}
