package com.vulntrend.batch.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;

/** 우리 예외가 아닌 실패(저장소 연결 끊김 등)로 실행이 끝나도 종료 코드는 1 이다 */
@DisplayName("예상하지 못한 실패의 종료 코드")
class RunExitCodeTest {

    @Test
    @DisplayName("저장소에 닿지 못해 실행이 멈춰도 배치 프로세스는 종료 코드 1 로 끝난다")
    void unexpectedFailureExitsWithOne() {
        List<Integer> exitCodes = new ArrayList<>();
        SpringApplication application = new SpringApplication(StoreDown.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addListeners((ApplicationListener<ExitCodeEvent>) event -> exitCodes.add(event.getExitCode()));

        Throwable failure = catchThrowable(() -> application.run());

        assertThat(failure).isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(exitCodes).containsExactly(1);
    }

    /** 저장소에 닿지 못하는 실행 하나와 실제 종료 코드 설정만 담은 최소 설정 */
    @Configuration(proxyBeanMethods = false)
    @Import(RunExitCodes.class)
    static class StoreDown {

        @Bean
        CommandLineRunner storeDown() {
            return args -> {
                throw new DataAccessResourceFailureException("connection refused");
            };
        }
    }
}
