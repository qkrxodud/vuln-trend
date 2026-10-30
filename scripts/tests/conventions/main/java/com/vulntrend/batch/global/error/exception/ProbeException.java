package com.vulntrend.batch.global.error.exception;

public class ProbeException extends RuntimeException {

    public ProbeException(String m) {
        super(m);
    }

    static class Detail {
        Detail() {
        }
    }
}
