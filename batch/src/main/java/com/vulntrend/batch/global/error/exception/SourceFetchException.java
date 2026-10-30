package com.vulntrend.batch.global.error.exception;

import com.vulntrend.batch.global.error.ErrorCode;
import com.vulntrend.batch.global.error.VulnTrendException;

/**
 * 외부 소스(NVD·KEV·EPSS·OSV·뉴스) 수집 실패. 수집 순회는 이 예외를 받으면 그 소스만 실패로 기록하고 다음 소스로 넘어간다.
 * global 은 domain 을 참조하지 않으므로 소스는 소문자 이름(SourceName.getKey())으로 받는다.
 * 상세에는 응답 본문·요청 URL·API 키를 넣지 않는다.
 */
public class SourceFetchException extends VulnTrendException {

    public SourceFetchException(String sourceKey, String detail) {
        super(ErrorCode.SOURCE_FETCH_FAILED, "Source " + sourceKey + ": " + detail);
    }
}
