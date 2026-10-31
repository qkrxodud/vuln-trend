package com.vulntrend.batch.global.error.exception;

import com.vulntrend.batch.global.error.ErrorCode;
import com.vulntrend.batch.global.error.VulnTrendException;

/**
 * 사이트 데이터 게시(git add·commit·push) 실패. 이 예외가 나면 그 실행은 실패로 끝난다(종료 코드 1, WBS 4.3).
 * 상세에는 git 단계 이름과 종료 코드만 넣는다. git 출력은 원격 주소·인증 정보를 담을 수 있어 넣지 않는다.
 */
public class PublishException extends VulnTrendException {

    public PublishException(String detail) {
        super(ErrorCode.PUBLISH_FAILED, detail);
    }
}
