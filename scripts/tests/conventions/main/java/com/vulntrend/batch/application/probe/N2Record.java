package com.vulntrend.batch.application.probe;

public record N2Record(int a) {

    public N2Record {
        if (a < 0) {
            throw new IllegalArgumentException();
        }
    }

    public N2Record(String s) {
        this(s.length());
    }
}
