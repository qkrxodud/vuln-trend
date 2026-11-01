package com.vulntrend.batch.domain.summary;

import java.util.OptionalInt;

/**
 * 요약 호출 한 번의 결과. 같은 CVE 가 같은 결과를 몇 번 받으면 다시 부르지 않는지는 finalAfter 가 정한다(일 상한 보존, 기획서 6장).
 * 만든 것·거절된 것은 한 번으로 끝, 버린 것은 한 번 더 시도해 두 번째로 버려지면 끝(사용자 결정 2026-11-01),
 * 실패(FAILED: 연결·한도·모양 오류)는 몇 번이든 다음 실행에서 다시 부른다.
 */
public enum SummaryOutcome {
    GENERATED, DISCARDED, REFUSED, FAILED;

    /** 같은 CVE 가 이 결과를 이만큼 받으면 다시 부르지 않는다. 비어 있으면 몇 번이든 다시 부른다 */
    public OptionalInt finalAfter() {
        return switch (this) {
            case GENERATED, REFUSED -> OptionalInt.of(1);
            case DISCARDED -> OptionalInt.of(2);
            case FAILED -> OptionalInt.empty();
        };
    }

    /** 답을 받지 못한 호출. 하루 상한이 아니라 실패 안전 상한으로 센다(SummaryCallBudget) */
    public boolean isFailed() {
        return this == FAILED;
    }
}
