package com.ndmsystems.coala;

import com.ndmsystems.coala.message.CoAPMessage;
import com.ndmsystems.coala.message.CoAPMessageCode;
import com.ndmsystems.coala.message.CoAPMessagePayload;

/**
 * Created by bas on 28.11.16.
 */

public class CoAPResourceOutput {
    public final CoAPMessagePayload payload;
    public final CoAPMessageCode code;
    public final CoAPMessage.MediaType mediaType;

    public CoAPResourceOutput(CoAPMessagePayload payload, CoAPMessageCode code, CoAPMessage.MediaType mediaType) {
        this.payload = payload;
        this.code = code;
        this.mediaType = mediaType;
    }
}
