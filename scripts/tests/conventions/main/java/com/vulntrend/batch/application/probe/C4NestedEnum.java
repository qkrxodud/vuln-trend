package com.vulntrend.batch.application.probe;

public class C4NestedEnum {

    enum Kind {
        A("a") {
            @Override
            String w() {
                return "x";
            }
        },
        B("b");

        private final String wire;

        Kind(String wire) {
            this.wire = wire;
        }

        String w() {
            return wire;
        }
    }
}
