package com.ndmsystems.coala.layers.response;

/**
 * Created by Владимир on 23.08.2017.
 */

public interface ResponseHandler {

    void onResponse(ResponseData responseData);
    void onError(Throwable error);

}
