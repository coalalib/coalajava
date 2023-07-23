package com.ndmsystems.coala.message;

/**
 * Created by Владимир on 26.06.2017.
 */

public enum CoAPRequestMethod {

    /**
     * The GET code.
     */
    GET("GET"),

    /**
     * The POST code.
     */
    POST("POST"),

    /**
     * The PUT code.
     */
    PUT("PUT"),

    /**
     * The DELETE code.
     */
    DELETE("DELETE");

    public final String value;

    /**
     * Instantiates a new type with the specified integer value.
     *
     * @param value the integer value
     */
    CoAPRequestMethod(String value) {
        this.value = value;
    }
}

