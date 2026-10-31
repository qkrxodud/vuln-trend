package com.vulntrend.batch.domain.summary;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 요약 한 건의 결과. 만들었거나(GENERATED), 명령어나 원문에 없는 버전이 섞여 버렸거나(DISCARDED),
 * Claude 가 거절했거나(REFUSED, 대체 모델까지 거절한 경우 포함), 실패했다(FAILED).
 * 버린 것·거절된 것·실패한 것 모두 비치명이다 — 그 CVE 는 요약 없이 사이트에 실린다(기획서 6장).
 * model 은 요청한 모델이 아니라 실제로 답한 모델이다(거절 대비 대체 모델이 답했을 수 있다).
 */
public class SummaryResult {

    @Getter
    private final SummaryOutcome outcome;
    private final KoreanSummary summary;
    private final String model;

    @Builder(access = AccessLevel.PRIVATE)
    private SummaryResult(SummaryOutcome outcome, KoreanSummary summary, String model) {
        this.outcome = outcome;
        this.summary = summary;
        this.model = model;
    }

    /** 세 문장이 다 있어야 하고, 명령어도 원문에 없는 버전도 없어야 요약으로 쓴다 */
    public static SummaryResult from(KoreanSummary summary, String model, SummarySource source) {
        if (summary.isIncomplete()) {
            return answered(SummaryOutcome.FAILED, model);
        }
        if (summary.hasCommand() || summary.hasUnknownVersion(source)) {
            return answered(SummaryOutcome.DISCARDED, model);
        }
        return SummaryResult.builder().outcome(SummaryOutcome.GENERATED).summary(summary).model(model).build();
    }

    public static SummaryResult refused(String model) {
        return answered(SummaryOutcome.REFUSED, model);
    }

    public static SummaryResult failed() {
        return SummaryResult.builder().outcome(SummaryOutcome.FAILED).build();
    }

    public boolean isGenerated() {
        return outcome == SummaryOutcome.GENERATED;
    }

    public boolean isDiscarded() {
        return outcome == SummaryOutcome.DISCARDED;
    }

    public boolean isRefused() {
        return outcome == SummaryOutcome.REFUSED;
    }

    public boolean isFailed() {
        return outcome == SummaryOutcome.FAILED;
    }

    public Optional<KoreanSummary> getSummary() {
        return Optional.ofNullable(summary);
    }

    public Optional<String> getModel() {
        return Optional.ofNullable(model);
    }

    private static SummaryResult answered(SummaryOutcome outcome, String model) {
        return SummaryResult.builder().outcome(outcome).model(model).build();
    }
}
