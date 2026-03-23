package com.ndmsystems.coala.layers.response

import com.ndmsystems.coala.exceptions.BaseCoalaThrowable

interface ResponseHandler {
    fun onResponse(responseData: ResponseData)
    fun onError(error: BaseCoalaThrowable)
}