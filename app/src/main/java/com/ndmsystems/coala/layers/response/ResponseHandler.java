package com.ndmsystems.coala.layers.response;

import com.ndmsystems.coala.exceptions.BaseCoalaThrowable;

/**
 * Created by Владимир on 23.08.2017.
 */

public interface ResponseHandler {

    void onResponse(ResponseData responseData);
    void onError(BaseCoalaThrowable error);

}
