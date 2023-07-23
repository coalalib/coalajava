package com.ndmsystems.coala.utils;

/**
 * Created by Владимир on 28.06.2017.
 */

public class Reference<T> {

    private T object;

    public Reference(T object){
        this.object = object;
    }

    public void set(T object){
        this.object = object;
    }

    public T get(){
        return object;
    }
}
