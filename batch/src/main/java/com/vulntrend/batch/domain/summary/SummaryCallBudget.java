package com.vulntrend.batch.domain.summary;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 오늘(한국 달력일) 남은 요약 호출. 두 상한을 따로 센다(사용자 결정 2026-11-01).
 * - 하루 상한: 답을 받은 호출(만들고·버리고·거절된 것)만 센다. 실패는 요약 자리를 쓰지 않는다.
 * - 실패 안전 상한: 실패(연결·한도·열쇠·해석 오류·미완성)만 센다. 열쇠 오류처럼 실행마다 헛호출이 쌓이는 것을 막는다.
 * 한 실행 안에서만 쓰고 버린다(빈이 아니다).
 */
public class SummaryCallBudget {

    @Getter
    private final int answeredLimit;
    @Getter
    private final int failureLimit;
    private int answered;
    private int failed;

    @Builder(access = AccessLevel.PRIVATE)
    private SummaryCallBudget(int answeredLimit, int failureLimit, int answered, int failed) {
        this.answeredLimit = answeredLimit;
        this.failureLimit = failureLimit;
        this.answered = answered;
        this.failed = failed;
    }

    public static SummaryCallBudget of(int answeredLimit, int failureLimit, int answeredToday, int failedToday) {
        return SummaryCallBudget.builder()
                .answeredLimit(answeredLimit)
                .failureLimit(failureLimit)
                .answered(answeredToday)
                .failed(failedToday)
                .build();
    }

    public void spend(SummaryOutcome outcome) {
        if (outcome.isFailed()) {
            failed++;
            return;
        }
        answered++;
    }

    public boolean isUsedUp() {
        return isAnsweredUsedUp() || isFailureLimitReached();
    }

    public boolean isAnsweredUsedUp() {
        return answered >= answeredLimit;
    }

    public boolean isFailureLimitReached() {
        return failed >= failureLimit;
    }
}
