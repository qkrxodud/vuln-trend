package com.vulntrend.batch.application.probe;

public class C3Deep {

    static class A {
        static class B {
            protected static class C<T extends Comparable<T>> {
                public C(T value,
                         int x) {
                }
            }
        }
    }
}
