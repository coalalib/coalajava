package com.ndmsystems.coala.helpers;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by bas on 15.11.16.
 */

public class RandomGenerator {

    private static final long MAX_UINT = 4294967296L;

    private static Random random = new Random();

    public static byte[] getRandom(int size) {
        byte[] b = new byte[size];
        random.nextBytes(b);
        return b;
    }

    public static Long getRandomUnsignedIntAsLong() {
        return ThreadLocalRandom.current().nextLong(0, MAX_UINT);
    }

}
