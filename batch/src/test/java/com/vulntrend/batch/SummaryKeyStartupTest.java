package com.vulntrend.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.global.config.SummaryProperties;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;

/**
 * 실제 application.properties 를 올리고 시스템 환경 변수와 루트 .env 를 뺀 채로 시작해 본다(NvdApiKeyStartupTest 와 같은 방식).
 * 요약은 비치명 단계라(기획서 6장) 열쇠가 없어도 배치가 시작해야 한다 — NVD 열쇠와 반대.
 */
@DisplayName("배치 시작 — 요약 열쇠")
class SummaryKeyStartupTest {

    private static final String NO_ENV_FILE = "--vulntrend.env-file=/nonexistent/vuln-trend.env";
    private static final String NVD_KEY = "--NVD_API_KEY=nvd-key-7f3a9c";
    private static final String NO_RUNNER = "--batch.runner.enabled=false";

    @Test
    @DisplayName("요약 열쇠가 없어도 배치는 시작하고 요약만 꺼진다")
    void startsWithoutSummaryKey() {
        try (ConfigurableApplicationContext context = start(NO_ENV_FILE, NVD_KEY, NO_RUNNER)) {
            assertThat(context.isRunning()).isTrue();
            assertThat(context.getBean(SummaryProperties.class).isNotConfigured()).isTrue();
        }
    }

    @Test
    @DisplayName("요약 열쇠와 모델 이름을 주면 그 모델 이름으로 요약을 켠다")
    void bindsModelName() {
        try (ConfigurableApplicationContext context = start(NO_ENV_FILE, NVD_KEY, NO_RUNNER,
                "--SUMMARY_API_KEY=summary-key-41c9e7", "--SUMMARY_MODEL=some-model")) {
            SummaryProperties properties = context.getBean(SummaryProperties.class);
            assertThat(properties.isNotConfigured()).isFalse();
            assertThat(properties.getModel()).isEqualTo("some-model");
        }
    }

    private static ConfigurableApplicationContext start(String... args) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        SpringApplication application = new SpringApplication(VulnTrendBatchApplication.class, PostgresTestConfig.class);
        application.setEnvironment(environment);
        return application.run(args);
    }
}
