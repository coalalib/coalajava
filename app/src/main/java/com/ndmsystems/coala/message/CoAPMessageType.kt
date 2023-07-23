package com.ndmsystems.coala.message

/**
 * Instantiates a new type with the specified integer value.
 *
 * @param value the integer value
 */
enum class CoAPMessageType(val value: Int) {
    CON(0), NON(1), ACK(2), RST(3);

    companion object {
        /**
         * Converts an integer into its corresponding message type.
         *
         * @param value the integer value
         * @return the message type
         * @throws IllegalArgumentException if the integer value is unrecognized
         */
        @JvmStatic
        fun valueOf(value: Int): CoAPMessageType {
            return when (value) {
                0 -> CON
                1 -> NON
                2 -> ACK
                3 -> RST
                else -> throw IllegalArgumentException("Unknown CoAP type $value")
            }
        }
    }
}