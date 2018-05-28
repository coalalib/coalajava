package com.ndmsystems.coala.exceptions;

import com.ndmsystems.coala.message.CoAPMessageCode;

public class WrongAuthDataException extends CoAPException {

    public WrongAuthDataException(CoAPMessageCode code, String message) {
        super(code, message);
    }
}
