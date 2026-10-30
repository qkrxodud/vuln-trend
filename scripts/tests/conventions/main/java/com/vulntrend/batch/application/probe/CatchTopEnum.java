package com.vulntrend.batch.application.probe;

// 잡아야 함: 최상위 enum 손 생성자
public enum CatchTopEnum {
    A("a");

    private final String wire;

    CatchTopEnum(String wire) {
        this.wire = wire;
    }
}
