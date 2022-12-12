package com.ndmsystems.coala;

import com.ndmsystems.coala.message.CoAPRequestMethod;

public class CoAPResource {

    public final CoAPRequestMethod method;
    public final String path;
    private final CoAPResourceHandler handler;

    public CoAPResource(CoAPRequestMethod method, String path, CoAPResourceHandler handler) {
        this.method = method;
        this.path = path;
        this.handler = handler;
    }

    public CoAPResourceHandler getHandler() {
        return handler;
    }

    public boolean doesMatch(String path) {
        return this.path.equals(path);
    }

    public boolean doesMatch(String path, CoAPRequestMethod method) {
        return (this.path.equals(path) && this.method.equals(method));
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (other == this) return true;
        if (!(other instanceof CoAPResource)) return false;
        CoAPResource otherResource = (CoAPResource) other;
        return method == otherResource.method && path.equals(otherResource.path);
    }

    abstract public static class CoAPResourceHandler {
        abstract public CoAPResourceOutput onReceive(CoAPResourceInput inputData);
    }
}
