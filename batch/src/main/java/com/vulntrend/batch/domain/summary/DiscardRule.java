package com.vulntrend.batch.domain.summary;

/** 요약을 버린 규칙(SummaryGuard). 실행 기록에는 이 이름 그대로 남는다 */
public enum DiscardRule {
    COMMAND, UNKNOWN_VERSION
}
