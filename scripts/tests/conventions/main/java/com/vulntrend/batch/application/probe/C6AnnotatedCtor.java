package com.vulntrend.batch.application.probe;

public class C6AnnotatedCtor {

    @Deprecated
    public C6AnnotatedCtor() {
    }

    static class X {
        @Deprecated public X(int a) {
        }
    }
}
