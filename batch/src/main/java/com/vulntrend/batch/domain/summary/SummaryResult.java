package com.vulntrend.batch.domain.summary;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;

/**
 * 요약 한 건의 결과. 만들었거나(GENERATED), 명령어가 섞여 버렸거나(DISCARDED), Claude 가 거절했거나(REFUSED), 실패했다(FAILED).
 * 버린 것·거절된 것·실패한 것 모두 비치명이다 — 그 CVE 는 요약 없이 사이트에 실린다(기획서 6장).
 */
public class SummaryResult {

    private final Status status;
    private final KoreanSummary summary;
    private final String model;

    @Builder(access = AccessLevel.PRIVATE)
    private SummaryResult(Status status, KoreanSummary summary, String model) {
        this.status = status;
        this.summary = summary;
        this.model = model;
    }

    /** 세 문장이 다 있어야 하고 명령어가 없어야 요약으로 쓴다. 명령어가 섞인 요약은 버린다 */
    public static SummaryResult from(KoreanSummary summary, String model) {
        if (summary.isIncomplete()) {
            return failed();
        }
        if (summary.hasCommand()) {
            return SummaryResult.builder().status(Status.DISCARDED).build();
        }
        return SummaryResult.builder().status(Status.GENERATED).summary(summary).model(model).build();
    }

    public static SummaryResult refused() {
        return SummaryResult.builder().status(Status.REFUSED).build();
    }

    public static SummaryResult failed() {
        return SummaryResult.builder().status(Status.FAILED).build();
    }

    public boolean isGenerated() {
        return status == Status.GENERATED;
    }

    public boolean isDiscarded() {
        return status == Status.DISCARDED;
    }

    public boolean isRefused() {
        return status == Status.REFUSED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public Optional<KoreanSummary> getSummary() {
        return Optional.ofNullable(summary);
    }

    public Optional<String> getModel() {
        return Optional.ofNullable(model);
    }

    private enum Status {
        GENERATED, DISCARDED, REFUSED, FAILED
    }
}
