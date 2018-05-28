package com.ndmsystems.coala.layers.response;

import com.ndmsystems.coala.exceptions.CoAPException;
import com.ndmsystems.coala.exceptions.WrongAuthDataException;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageType;

class ResponseErrorFactory {

    ResponseErrorFactory() {
    }

    CoAPException proceed(CoAPMessage message){
        if (message.getType() == CoAPMessageType.RST){
            return new CoAPException(message.getCode(), message.getPayload() == null ? "Request has been reset!" : message.getPayload().toString());
        } else if(message.getCode().getCodeClass() != 2){
            // TODO Удалить этот if с return, после того как от роутера будет приходить ошибка с кодом CoAPMessageCode.CoapCodeUnauthorized на неверный логин\пароль
            if(message.getPayload() != null && message.getPayload().toString().equals("Wrong login or password"))
                return new WrongAuthDataException(message.getCode(), CoAPMessageCode.CoapCodeUnauthorized.name());
            return proceedByResponseCode(message);
        }
        return null;
    }

    private CoAPException proceedByResponseCode(CoAPMessage message){
        switch (message.getCode()){
            case CoapCodeUnauthorized:
                return new WrongAuthDataException(message.getCode(), CoAPMessageCode.CoapCodeUnauthorized.name());
                default: return new CoAPException(message.getCode(), message.getPayload() == null ? "Request has been reset!" : message.getPayload().toString());
        }
    }
}
