package com.ndmsystems.coala.helpers;

import java.util.List;

/**
 * Created by bas on 18.10.16.
 */
public class StringHelper {

    public static String join(List<String> list, String delim) {

        StringBuilder sb = new StringBuilder();

        String loopDelim = "";

        for(String s : list) {

            sb.append(loopDelim);
            sb.append(s);

            loopDelim = delim;
        }

        return sb.toString();
    }

    private static final int UNIT = 1024;

    public static String getHumanReadableByteString(Long bytes) {
        return getHumanReadableBitOrByteString(bytes, true);
    }

    public static String getHumanReadableBitString(Long bytes) {
        return getHumanReadableBitOrByteString(bytes, false);
    }

    private static String getHumanReadableBitOrByteString(Long bytes, Boolean isBytes) {
        if (bytes < UNIT) return String.format((isBytes ? "%d bytes" : "%d bits"), bytes);
        int exp = (int) (Math.log(bytes) / Math.log(UNIT));
        String pre = "kMGTPE".charAt(exp-1) + ("");
        return String.format((isBytes ? "%.1f %sbytes" : "%.1f %sbits"), bytes / Math.pow(UNIT, exp), pre);
    }
}
