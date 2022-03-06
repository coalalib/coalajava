package com.ndmsystems.coala.exceptions;


import com.ndmsystems.coala.message.CoAPMessageCode;

import org.jetbrains.annotations.Nullable;

/**
 * Created by Владимир on 21.07.2017.
 */

public class CoAPException extends BaseCoalaThrowable {

    private CoAPMessageCode code;
    private Integer payloadErrorCode;

    public CoAPException(CoAPMessageCode code, String message) {
        super(message);
        this.code = code;
        payloadErrorCode = null;
    }

    public CoAPException(String message, CoAPMessageCode code, Integer payloadErrorCode) {
        super(message != null && !message.isEmpty() ? message : "Handle payload error:" + payloadErrorCode);
        this.code = code;
        this.payloadErrorCode = payloadErrorCode;
    }

    public CoAPMessageCode getCode() {
        return code;
    }

    @Nullable
    public Integer getPayloadErrorCode() {
        return payloadErrorCode;
    }

    public PayloadError getPayloadError(){
        return PayloadError.getByCode(payloadErrorCode);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " code: " + code + ", code.value: " + code.value + ", payloadErrorCode: " + payloadErrorCode + ", message: " + getMessage();
    }
}
