package com.vulntrend.batch.runner.rules;

import com.vulntrend.batch.global.error.exception.ProbeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;

// 규칙 11 시험 — 로그에 넣는 예외 문장
@Slf4j
public class LogProbe {
    void framework(AuthenticationException failure) {
        log.info("Google sign-in did not complete: {}", failure.getMessage());   // 잡을 것: 프레임워크 예외
    }

    void ours(ProbeException refused) {
        log.info("Request refused: {}", refused.getMessage());                   // 잡지 말 것: 우리 예외(global/error)
    }

    void chained(Exception problem) {
        log.warn("Nested: {}", problem.getCause().getMessage());                 // 잡을 것: 원인 사슬
        log.error("Unexpected server error.", problem);                          // 잡지 말 것: 예외 객체(스택)
        String text = problem.getMessage();                                      // 잡지 말 것: 로그가 아님
        log.debug("Localized: {}", problem.getLocalizedMessage());               // 잡을 것
    }

    void unknown() {
        log.info("No declaration: {}", mystery.getMessage());                    // 잡을 것: 선언 타입을 못 찾음
    }

    void caught() {
        try {
            run();
        } catch (ProbeException ok) {
            log.info("Caught ours: {}", ok.getMessage());                        // 잡지 말 것: catch 의 우리 예외
        } catch (final IllegalStateException bad) {
            log.info("Caught theirs: {}", bad.getMessage());                     // 잡을 것: catch 의 외부 예외
        }
    }

    void sameNameTwice(ProbeException e) {
        log.info("First declaration ours: {}", e.getMessage());                 // 잡지 말 것: 가장 가까운 앞 선언(39행)이 우리 예외 — 51행의 같은 이름 e 는 다른 범위
        try {
            run();
        } catch (IllegalStateException | IllegalArgumentException e2) {
            log.info("Multi-catch theirs: {}", e2.getMessage());                 // 잡을 것: 다중 catch 의 외부 예외
        }
    }

    void sameNameLater() {
        try {
            run();
        } catch (IOException e) {
            log.info("Later declaration theirs: {}", e.getMessage());            // 잡을 것: 같은 이름 e 의 다른 선언(외부) — 먼저 것만 보면 놓침
        }
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LogProbe.class);

    void guardianShapes(Exception e) {
        log.info("Multi-line {}",
                e.getMessage());                                                 // 잡을 것: 여러 줄 호출(시작 줄에 알림)
        LOGGER.info("Other logger name {}", e.getMessage());                     // 잡을 것: LOGGER 이름
        log.info("toString {}", e.toString());                                   // 잡을 것: 예외의 toString(= 종류 + 문장)
        log.info("Call result {}", org.springframework.core.NestedExceptionUtils.getMostSpecificCause(e).getMessage()); // 잡을 것
        log.info("Placeholder gets exception {}", e);                            // 잡을 것: {} 자리에 예외 객체(toString)
        log.atInfo().log("Fluent {}", e.getMessage());                           // 잡을 것: fluent API
        StringBuilder text = new StringBuilder();
        log.info("Not an exception {}", text.toString());                        // 잡지 말 것: 예외 아닌 객체의 toString
        log.warn("Two placeholders {} {}", text, e);                             // 잡을 것: {} 둘·인자 둘이면 마지막 e 도 toString
    }

    private final Exception kept = new IllegalStateException();

    void fieldException() {
        log.info("Field exception {}", kept.getMessage());                       // 잡을 것: 필드 예외 — 선언을 못 찾아 위반(닫힌 쪽)
    }
}
