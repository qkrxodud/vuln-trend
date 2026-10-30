package com.vulntrend.batch.application.probe;

public class C1NestedStatic {

    static final class Slot {
        private final int a;

        private Slot(int a) {
            this.a = a;
        }
    }
}
