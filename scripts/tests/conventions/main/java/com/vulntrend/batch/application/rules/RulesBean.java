package com.vulntrend.batch.application.rules;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

// 다른 규칙 회귀: 빈 상태(3·3-b), setter(4, @Override 제외), if (!(2), 람다 !(7), 문자열 속 글자는 무시
@Service
public class RulesBean {

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private int counter;
    private final String name = "a";
    private static final String GLOB = "/api/**";

    public void setName(String value) {
    }

    @Override
    public void setReadListener(Object listener) {
    }

    void run(String s) {
        if (!s.isEmpty()) {
            return;
        }
        String text = "if (!x) -> !y @Value(";
        Predicate<String> p = v -> !v.isEmpty();
    }
}
