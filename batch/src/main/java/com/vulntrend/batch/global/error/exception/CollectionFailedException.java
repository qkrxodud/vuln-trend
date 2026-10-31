package com.vulntrend.batch.global.error.exception;

import com.vulntrend.batch.global.error.ErrorCode;
import com.vulntrend.batch.global.error.VulnTrendException;

/**
 * 수집(1~4단계)이 하나라도 실패해 그 실행을 실패로 끝낸다(기획서 6장 실패 규칙, WBS 2.4·4.3). 점수·출력·게시는 하지 않았다.
 * 상세에는 실패한 소스 이름만 넣는다. 소스별 진행 기록은 이미 남았으므로 다음 실행이 같은 구간을 다시 가져온다.
 */
public class CollectionFailedException extends VulnTrendException {

    public CollectionFailedException(String detail) {
        super(ErrorCode.COLLECTION_FAILED, detail);
    }
}
