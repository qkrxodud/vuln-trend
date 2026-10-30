package com.vulntrend.batch.global.error;

import lombok.Getter;

/** 배치 예외의 공통 조상. 문장은 고정 문구 + 우리가 정한 상세만 담는다(응답 본문·URL·API 키 금지) */
@Getter
public class VulnTrendException extends RuntimeException {

    private final ErrorCode errorCode;

    public VulnTrendException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + " " + detail);
        this.errorCode = errorCode;
    }
}
