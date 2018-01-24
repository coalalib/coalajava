package com.ndmsystems.coala.helpers;

public class TimeHelper {

    public static Long getTimeForMeasurementInMilliseconds() {
        return System.nanoTime() / 1000000;
    }
}