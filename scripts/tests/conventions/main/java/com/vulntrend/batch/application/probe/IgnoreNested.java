package com.vulntrend.batch.application.probe;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

// 잡지 말아야 함: @Builder private 생성자, Lombok 생성자, 이름이 같은 지역 사용·호출, 익명 클래스·람다, 문자열·주석 안 글자
public class IgnoreNested {

    // private Slot(String x) { 주석 안 }
    /* Slot(String y) { 블록 주석 } */
    private static final String TEXT = "Slot( { { private Slot(";
    private static final String BLOCK = """
            Slot( {
              Holder( {
            """;
    private static final char BRACE = '{';

    Slot make(String change) {
        Slot slot = Slot.valid(change);
        Runnable r = () -> {
            Slot inner = new Slot(change, null);
        };
        Object anon = new Object() {
            @Override
            public String toString() {
                return "Slot(";
            }
        };
        return new Slot(change,
                null);
    }

    private static final class Slot {
        private final String change;
        private final String other;

        @Builder(access = AccessLevel.PRIVATE)
        private Slot(String change, String other) {
            this.change = change;
            this.other = other;
        }

        static Slot valid(String change) {
            return Slot.builder().change(change).build();
        }

        Slot copy() {
            return Slot.valid(change);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Holder {
        private final String value;

        static Holder of(String value) {
            return new Holder(value);
        }
    }

    static class Plain {
        @Builder
        private Plain(String a) {
        }
    }

    enum Kind {
        A, B;

        boolean isA() {
            return this == A;
        }
    }
}
