package com.vulntrend.batch.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.vulntrend.batch.global.error.exception.CollectionFailedException;
import com.vulntrend.batch.global.error.exception.OutputWriteException;
import com.vulntrend.batch.global.error.exception.PublishException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeEvent;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 실패한 실행의 종료 코드(WBS 4.3, 사용자 결정 2026-10-31) — cron·Actions 가 실패를 알 수 있어야 한다 */
@DisplayName("실패한 실행의 종료 코드")
class FailedRunExitCodeTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    @DisplayName("수집·출력·게시가 실패해 실행이 끝나면 종료 코드는 1 이다")
    void exitCodeOne(VulnTrendException failure) {
        assertThat(failure).isInstanceOf(ExitCodeGenerator.class);
        assertThat(((ExitCodeGenerator) failure).getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("하루 한 번 실행이 실패하면 배치 프로세스가 종료 코드 1 로 끝난다")
    void springReportsExitCodeOne() {
        List<Integer> exitCodes = new ArrayList<>();
        SpringApplication application = new SpringApplication(FailingRun.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addListeners((ApplicationListener<ExitCodeEvent>) event -> exitCodes.add(event.getExitCode()));

        Throwable failure = catchThrowable(() -> application.run());

        assertThat(failure).isInstanceOf(PublishException.class);
        assertThat(exitCodes).containsExactly(1);
    }

    static Stream<Named<VulnTrendException>> failures() {
        return Stream.of(
                Named.of("수집 실패", new CollectionFailedException("[KEV]")),
                Named.of("데이터 파일 쓰기 실패", new OutputWriteException("index.json")),
                Named.of("게시 실패", new PublishException("git push failed with exit code 128.")));
    }

    /** 게시가 실패하는 실행 하나만 담은 최소 설정(저장소·외부 소스 없음) */
    @Configuration(proxyBeanMethods = false)
    static class FailingRun {

        @Bean
        CommandLineRunner failingRun() {
            return args -> {
                throw new PublishException("git push failed with exit code 128.");
            };
        }
    }
}
