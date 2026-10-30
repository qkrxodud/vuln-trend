package com.vulntrend.batch.application.probe;

// 잡지 말아야 함: record 생성자(record 는 dto 규칙이 따로 본다)
public record IgnoreRecord(String a) {
    public IgnoreRecord(String a) {
        this.a = a;
    }
}
