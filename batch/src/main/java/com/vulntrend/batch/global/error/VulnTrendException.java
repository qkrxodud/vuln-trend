package com.vulntrend.batch.global.error;

import lombok.Getter;
import org.springframework.boot.ExitCodeGenerator;

/**
 * 배치 예외의 공통 조상. 문장은 고정 문구 + 우리가 정한 상세만 담는다(응답 본문·URL·API 키 금지).
 * 이 예외가 하루 한 번 실행 밖으로 나가면 그 실행은 실패다: Spring 이 종료 코드 1 로 프로세스를 끝내
 * cron·Actions 가 실패를 안다(WBS 4.3, 사용자 결정 2026-10-31).
 */
@Getter
public class VulnTrendException extends RuntimeException implements ExitCodeGenerator {

    /** 실패한 실행의 프로세스 종료 코드 */
    private static final int FAILED_RUN = 1;

    private final ErrorCode errorCode;

    public VulnTrendException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + " " + detail);
        this.errorCode = errorCode;
    }

    @Override
    public int getExitCode() {
        return FAILED_RUN;
    }
}
