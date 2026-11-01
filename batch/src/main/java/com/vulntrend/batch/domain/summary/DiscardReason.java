package com.vulntrend.batch.domain.summary;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 요약을 버린 이유: 걸린 규칙과 걸린 값(명령어 조각 또는 원문에 없는 버전). 실행 기록 한 줄로 남긴다(사용자 결정 2026-11-01).
 * 걸린 값은 모델이 쓴 글의 일부라 줄바꿈·제어 문자를 빈칸으로 바꾸고 60자까지만 든다 — 실행 기록 한 줄을 깨지 않게.
 */
@Getter
public class DiscardReason {

    private static final int MAX_FRAGMENT = 60;

    private final DiscardRule rule;
    private final String fragment;

    @Builder(access = AccessLevel.PRIVATE)
    private DiscardReason(DiscardRule rule, String fragment) {
        this.rule = rule;
        this.fragment = fragment;
    }

    public static DiscardReason of(DiscardRule rule, String fragment) {
        String oneLine = fragment.replaceAll("\\p{Cntrl}", " ");
        return DiscardReason.builder()
                .rule(rule)
                .fragment(oneLine.substring(0, Math.min(oneLine.length(), MAX_FRAGMENT)))
                .build();
    }
}
