package com.ndmsystems.coala.helpers;


import com.ndmsystems.coala.layers.blockwise.Block;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageOption;

import java.util.Random;

public class MessageHelper {

    private static int MAX_ID = 65535;
    private static int currentMessageID = new Random().nextInt(MAX_ID);

    public static synchronized int generateId() {
        if (currentMessageID < MAX_ID) {
            currentMessageID++;
        } else {
            currentMessageID = 1;
        }
        return currentMessageID;
    }

    public static String getMessageOptionsString(CoAPMessage message) {
        StringBuilder buf = new StringBuilder();

        for (CoAPMessageOption option : message.getOptions()) {
            switch (option.code) {
                case OptionBlock1:
                case OptionBlock2:
                    buf.append(option.code.toString()).append(" : '").append(option.value == null ? "null" : (Block.fromInt((Integer) option.value) + "'(" + option.value + ") "));
                    break;
                default:
                    buf.append(option.code.toString()).append(" : '").append(option.value == null ? "null" : option.value.toString()).append("' ");
            }
        }

        return buf.toString();
    }
}
