package com.ndmsystems.coala.layers.response;


import com.ndmsystems.coala.exceptions.CoAPException;
import com.ndmsystems.coala.exceptions.WrongAuthDataException;
import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessageType;

import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

class ResponseErrorFactory {

    ResponseErrorFactory() {
    }

    @Nullable
    CoAPException proceed(CoAPMessage message){
        if (message.getType() == CoAPMessageType.RST){
            return new CoAPException(message.getCode(), message.getPayload() == null ? "Request has been reset!" : message.getPayload().toString());
        } else if(message.getCode().getCodeClass() != 2){
            if(message.getPayload() == null) return proceedByResponseCode(message);

            if(message.getPayload().toString().equals("Wrong login or password")){
                return new WrongAuthDataException(message.getCode(), CoAPMessageCode.CoapCodeUnauthorized.name());
            }else if(message.getPayload().toString().contains("code")){
                return proceedByResponsePayloadErrorCode(message);
            }

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

    @Nullable
    private CoAPException proceedByResponsePayloadErrorCode(CoAPMessage message){
        CoAPException coAPException = null;
        try{
            JSONObject errorObject = new JSONObject(message.getPayload().toString());
            String errorMessage = errorObject.has("message") ? errorObject.getString("message") : "";
            int payloadErrorCode = errorObject.has("code") ? errorObject.getInt("code") : 0;
            coAPException = new CoAPException(errorMessage, message.getCode(), payloadErrorCode);
        }catch (JSONException e){
            e.printStackTrace();
        }
        return coAPException;
    }
}
