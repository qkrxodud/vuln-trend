package com.vulntrend.batch.global.error.exception;

import com.vulntrend.batch.global.error.ErrorCode;
import com.vulntrend.batch.global.error.VulnTrendException;

/** 도메인 규칙 위반(잘못된 수집 구간, 모르는 소스 이름 등) */
public class DomainValidationException extends VulnTrendException {

    public DomainValidationException(String detail) {
        super(ErrorCode.DOMAIN_RULE_VIOLATED, detail);
    }
}
