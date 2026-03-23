package com.ndmsystems.coala

import com.ndmsystems.coala.message.CoAPMessage
import com.ndmsystems.coala.message.CoAPMessageCode
import com.ndmsystems.coala.message.CoAPMessagePayload

class CoAPResourceOutput(val payload: CoAPMessagePayload?, val code: CoAPMessageCode, val mediaType: CoAPMessage.MediaType)