package com.ndmsystems.coala.message

/**
 * Created by Владимир on 26.06.2017.
 */
enum class CoAPRequestMethod
/**
 * Instantiates a new type with the specified integer value.
 *
 * @param value the integer value
 */(val value: String) {
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
}