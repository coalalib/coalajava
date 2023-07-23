package com.ndmsystems.coala.helpers

import com.ndmsystems.coala.layers.arq.Block
import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageOptionCode
import java.util.Random

object MessageHelper {
    private const val MAX_ID = 65535
    private var currentMessageID = Random().nextInt(MAX_ID)
    @JvmStatic
    @Synchronized
    fun generateId(): Int {
        if (currentMessageID < MAX_ID) {
            currentMessageID++
        } else {
            currentMessageID = 1
        }
        return currentMessageID
    }

    @JvmStatic
    fun getMessageOptionsString(message: CoAPMessage): String {
        val buf = StringBuilder()
        for (option in message.options) {
            when (option.code) {
                CoAPMessageOptionCode.OptionBlock1, CoAPMessageOptionCode.OptionBlock2 -> buf
                    .append(option.code).append(" : '")
                    .append(
                        if (option.value == null) "null" else Block(
                            (option.value as Int),
                            if (message.payload == null) null else message.payload.content
                        ).toString() + "'(" + option.value + ") "
                    )

                else -> buf
                    .append(option.code)
                    .append(" : '")
                    .append(if (option.value == null) "null" else option.value.toString()).append("' ")
            }
        }
        return buf.toString()
    }
}