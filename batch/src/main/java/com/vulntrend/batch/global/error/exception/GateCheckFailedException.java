package com.vulntrend.batch.global.error.exception;

import com.vulntrend.batch.global.error.ErrorCode;
import com.vulntrend.batch.global.error.VulnTrendException;

/**
 * Gate G 배치 점검(--gate-check)에 실패(❌) 항목이 있다. 그 실행은 종료 코드 1 로 끝난다(VulnTrendException).
 * "확인 필요"(⚠️)는 실패가 아니다. 상세에는 실패 항목 수만 넣는다 — 근거는 점검 표에 있다.
 */
public class GateCheckFailedException extends VulnTrendException {

    public GateCheckFailedException(String detail) {
        super(ErrorCode.GATE_CHECK_FAILED, detail);
    }
}
