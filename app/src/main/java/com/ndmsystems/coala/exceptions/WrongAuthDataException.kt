package com.ndmsystems.coala.exceptions

import com.ndmsystems.coala.message.CoAPMessageCode

class WrongAuthDataException(code: CoAPMessageCode, message: String?) : CoAPException(code, message)