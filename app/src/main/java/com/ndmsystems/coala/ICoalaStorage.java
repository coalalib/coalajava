package com.ndmsystems.coala;

public interface ICoalaStorage {

    void put(String key, Object obj);
    <T> T get(String key, Class<T> clz);
    void remove(String key);
}
