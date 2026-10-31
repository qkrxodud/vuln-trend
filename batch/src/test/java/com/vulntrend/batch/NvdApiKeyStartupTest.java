package com.vulntrend.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import com.vulntrend.batch.runner.BatchRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;

/**
 * 실제 application.properties 를 올리고 시스템 환경 변수와 루트 .env 를 뺀 채로 시작해 본다.
 * 검사 메서드만 부르는 테스트는 @PostConstruct 를 지워도 통과하므로 부족하다(vuln-trend-dev-conventions §1).
 */
@DisplayName("배치 시작 — NVD 열쇠")
class NvdApiKeyStartupTest {

    private static final String NO_ENV_FILE = "--vulntrend.env-file=/nonexistent/vuln-trend.env";

    @Test
    @DisplayName("NVD 열쇠가 없으면 배치가 시작하지 않는다")
    void refusesWithoutKey() {
        assertThatThrownBy(() -> start(NO_ENV_FILE).close())
                .rootCause()
                .hasMessageContaining("NVD_API_KEY");
    }

    @Test
    @DisplayName("NVD 열쇠가 있으면 같은 설정으로 시작한다")
    void startsWithKey() {
        try (ConfigurableApplicationContext context = start(NO_ENV_FILE, "--NVD_API_KEY=nvd-key-7f3a9c",
                "--batch.runner.enabled=false")) {
            assertThat(context.isRunning()).isTrue();
            assertThat(context.getBeansOfType(BatchRunner.class)).isEmpty();
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
