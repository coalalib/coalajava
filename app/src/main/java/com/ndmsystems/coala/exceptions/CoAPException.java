package com.ndmsystems.coala.exceptions;

import com.ndmsystems.coala.message.CoAPMessageCode;

/**
 * Created by Владимир on 21.07.2017.
 */

public class CoAPException extends Throwable {

    private CoAPMessageCode code;

    public CoAPException(CoAPMessageCode code, String message) {
        super(message);
        this.code = code;
    }

    public CoAPMessageCode getCode() {
        return code;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "  code: " + code + ", code.value: " + code.value + "  message: " + getMessage();
    }
}
