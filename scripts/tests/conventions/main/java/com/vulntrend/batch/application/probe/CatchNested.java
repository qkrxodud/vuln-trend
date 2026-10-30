package com.vulntrend.batch.application.probe;

// 잡아야 함: 중첩 static 클래스 손 생성자(Slot 류), 내부 클래스, 깊은 중첩, 중첩 enum, 여러 줄 시그니처
public class CatchNested {

    private static final class Slot {
        private final String change;

        private Slot(String change) {
            this.change = change;
        }
    }

    class Inner {
        Inner() {
        }
    }

    static class Outer {
        static class Middle {
            public static class Deep<T> {
                public Deep(T value,
                            String other) {
                }
            }
        }
    }

    enum Kind {
        A("a");

        private final String wire;

        Kind(String wire) {
            this.wire = wire;
        }
    }
}
