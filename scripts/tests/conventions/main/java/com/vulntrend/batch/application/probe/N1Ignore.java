package com.vulntrend.batch.application.probe;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

public class N1Ignore {

    static final class Slot {
        private final int a;

        @Builder(access = AccessLevel.PRIVATE)
        private Slot(int a) {
            this.a = a;
        }

        static Slot of(int a) {
            Slot slot = Slot.builder().a(a).build();
            return slot;
        }

        Slot copy() {
            return new Slot(a);
        }

        Slot make(int b) {
            return new Slot(
                    b);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    static final class Lom {
        private final int a;
    }

    enum Plain { A, B }

    Object anon() {
        Runnable r = () -> {
            Slot s = new Slot(1);
        };
        return new Object() {
            int x = 1;
        };
    }

    String strings() {
        String a = "Slot( { {";
        char c = '{';
        char d = '}';
        String tb = """
                Slot(
                {
                """;
        // private Slot(int a) {
        /* N1Ignore( { */
        return a + c + d + tb;
    }
}
