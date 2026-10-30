package com.vulntrend.batch.application.probe;

public class C8Generic<T> {

    private final T t;

    public C8Generic(T t) {
        this.t = t;
    }

    static class W {
        <U> W(U u) {
        }
    }
}
