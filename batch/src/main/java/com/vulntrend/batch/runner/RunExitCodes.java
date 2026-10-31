package com.vulntrend.batch.runner;

import org.springframework.boot.ExitCodeExceptionMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 실행이 예외로 끝나면 종료 코드는 1 이다(WBS 4.3, 사용자 결정 2026-10-31). 우리 예외(VulnTrendException)는 스스로 1 을 알리고,
 * 저장소 연결 끊김(DataAccessException) 같은 그 밖의 예외도 여기서 1 로 맞춘다 — cron·Actions 가 어떤 실패든 알 수 있게.
 */
@Configuration(proxyBeanMethods = false)
public class RunExitCodes {

    private static final int FAILED_RUN = 1;

    @Bean
    public ExitCodeExceptionMapper failedRunExitCode() {
        return failure -> FAILED_RUN;
    }
}
