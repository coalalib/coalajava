package com.ndmsystems.coala.layers.response

import com.ndmsystems.coala.exceptions.BaseCoalaThrowable

/**
 * Created by Владимир on 23.08.2017.
 */
interface ResponseHandler {
    fun onResponse(responseData: ResponseData)
    fun onError(error: BaseCoalaThrowable)
}