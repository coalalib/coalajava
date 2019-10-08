package com.ndmsystems.coala.helpers;


import com.ndmsystems.coala.layers.arq.Block;
import com.ndmsystems.coala.layers.arq.data.DataFactory;
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
                    buf
                            .append(option.code.toString()).append(" : '")
                            .append(option.value == null ? "null" :
                                    new Block(
                                            (Integer) option.value,
                                            message.getPayload() == null
                                                    ? DataFactory.createEmpty()
                                                    : DataFactory.create(message.getPayload().content)) + "'(" + option.value + ") ");
                    break;
                default:
                    buf
                            .append(option.code.toString())
                            .append(" : '")
                            .append(option.value == null ? "null" : option.value.toString()).append("' ");
            }
        }

        return buf.toString();
    }
}
