package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessagePayload

/**
 * Created by bas on 28.11.16.
 */
class CoAPResourceOutput(val payload: CoAPMessagePayload?, val code: CoAPMessageCode, val mediaType: CoAPMessage.MediaType)