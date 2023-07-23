package com.ndmsystems.coala.message;

public enum CoAPMessageType {

    CON(0),
    NON(1),
    ACK(2),
    RST(3);

    public final int value;

    /**
     * Instantiates a new type with the specified integer value.
     *
     * @param value the integer value
     */
    CoAPMessageType(int value) {
        this.value = value;
    }

    /**
     * Converts an integer into its corresponding message type.
     *
     * @param value the integer value
     * @return the message type
     * @throws IllegalArgumentException if the integer value is unrecognized
     */
    public static CoAPMessageType valueOf(final int value) {
        switch (value) {
            case 0:
                return CON;
            case 1:
                return NON;
            case 2:
                return ACK;
            case 3:
                return RST;
            default:
                throw new IllegalArgumentException("Unknown CoAP type " + value);
        }
    }
}
