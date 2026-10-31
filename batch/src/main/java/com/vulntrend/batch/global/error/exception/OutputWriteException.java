package com.vulntrend.batch.global.error.exception;

import com.vulntrend.batch.global.error.ErrorCode;
import com.vulntrend.batch.global.error.VulnTrendException;

/**
 * 사이트 데이터 파일(output.dir) 쓰기 실패. 이 예외가 나면 그 실행은 push 하지 않는다(반쪽 JSON 금지, 기획서 6장 실패 규칙).
 * 상세에는 파일의 상대 경로만 넣고, 예외 문장은 넣지 않는다.
 */
public class OutputWriteException extends VulnTrendException {

    public OutputWriteException(String detail) {
        super(ErrorCode.OUTPUT_WRITE_FAILED, detail);
    }
}
