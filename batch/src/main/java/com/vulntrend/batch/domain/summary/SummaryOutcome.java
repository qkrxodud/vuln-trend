package com.vulntrend.batch.domain.summary;

/**
 * 요약 호출 한 번의 결과. 만든 것·버린 것·거절된 것은 다시 부르지 않는다(일 상한 보존, 기획서 6장).
 * 실패(FAILED: 연결·한도·모양 오류)만 다음 실행에서 다시 부른다.
 */
public enum SummaryOutcome {
    GENERATED, DISCARDED, REFUSED, FAILED;

    public boolean isRetryable() {
        return this == FAILED;
    }
}
