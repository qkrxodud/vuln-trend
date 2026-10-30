package com.vulntrend.batch.application.probe;

// 잡지 말아야 함: 전체 이름 애너테이션 @lombok.Builder private 생성자, 타입 이름과 같은 이름의 메서드,
// 클래스 리터럴(Slot.class), 필드 초기화의 익명 클래스, 제네릭 반환 메서드
public class N4Shapes {

    private static final Class<?> TYPE = Inner.class;
    private final Object anon = new Object() {
        @Override
        public String toString() {
            return "N4Shapes(";
        }
    };

    void N4Shapes() {
    }

    <T> N4Shapes self(T t) {
        return this;
    }

    static final class Inner {
        @lombok.Builder(access = lombok.AccessLevel.PRIVATE) private Inner(int a) {
        }
    }
}
