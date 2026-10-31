package com.vulntrend.batch.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 배치의 모든 오류 종류. 새 오류는 여기에 먼저 추가한다 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    DOMAIN_RULE_VIOLATED("A domain rule was violated."),
    SOURCE_FETCH_FAILED("A data source did not answer or answered badly."),
    OUTPUT_WRITE_FAILED("A site data file could not be written.");

    private final String message;
}
