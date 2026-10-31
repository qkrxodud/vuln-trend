package com.vulntrend.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import com.vulntrend.batch.support.TestIsolationGuard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** 시험은 사용자의 실제 .env·실제 원격·실제 열쇠에 닿지 않는다(2026-11-01 사고 뒤 안전장치) */
@DisplayName("시험 격리")
class TestIsolationTest {

    @Test
    @DisplayName("시험은 저장소 루트의 .env 가 아니라 없는 파일을 설정으로 가리킨다")
    void testsPointAwayFromRootEnv() {
        String envFile = System.getProperty("vulntrend.env-file");

        assertThat(envFile).isNotBlank().doesNotEndWith("../.env");
        assertThat(Path.of(envFile)).doesNotExist();
    }

    @Test
    @DisplayName("시험이 실행하는 git 은 로컬 저장소 경로만 쓸 수 있다")
    void gitIsLocalOnly() {
        assertThat(System.getenv("GIT_ALLOW_PROTOCOL")).isEqualTo("file");
    }

    @Test
    @DisplayName("시험 안의 HTTPS 요청은 닿지 않는 대리 서버로 가서 바깥으로 나가지 못한다")
    void httpsGoesNowhere() {
        assertThat(System.getProperty("https.proxyHost")).isEqualTo("127.0.0.1");
        assertThat(System.getProperty("https.proxyPort")).isEqualTo("9");
    }

    @Test
    @DisplayName("실제 .env 를 가리키게 하더라도 그 안에 실제 원격 주소가 있으면 시험 배치는 시작하지 않는다")
    void realLookingEnvRefused(@TempDir Path dir) throws IOException {
        Path env = dir.resolve(".env");
        Files.writeString(env, "NVD_API_KEY=nvd-key-7f3a9c\nPUBLISH_REMOTE=https://example.invalid/owner/repo.git\n");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        SpringApplication application = new SpringApplication(VulnTrendBatchApplication.class, PostgresTestConfig.class);
        application.setEnvironment(environment);

        assertThatThrownBy(() -> application.run("--vulntrend.env-file=" + env, "--batch.runner.enabled=false").close())
                .hasStackTraceContaining("publish.remote is a network git remote")
                .hasStackTraceContaining("must not read the real .env");
    }

    @Nested
    @DisplayName("안전장치는")
    class Guard {

        @Test
        @DisplayName("GitHub 같은 네트워크 원격이면 시험 컨텍스트를 시작하지 않는다")
        void networkRemote() {
            assertThatThrownBy(() -> TestIsolationGuard.check(environment("publish.remote",
                    "https://github.com/owner/repo.git"))).hasMessageContaining("publish.remote");
            assertThatThrownBy(() -> TestIsolationGuard.check(environment("publish.remote",
                    "git@github.com:owner/repo.git"))).hasMessageContaining("publish.remote");
        }

        @Test
        @DisplayName("실제 같은 요약 열쇠나 NVD 열쇠가 보이면 시험 컨텍스트를 시작하지 않는다")
        void realLookingKeys() {
            assertThatThrownBy(() -> TestIsolationGuard.check(environment("summary.api-key", "sk-proj-abc123")))
                    .hasMessageContaining("summary.api-key").hasMessageNotContaining("abc123");
            assertThatThrownBy(() -> TestIsolationGuard.check(environment("nvd.api-key",
                    "0f8fad5b-d9cb-469f-a165-70867728950e"))).hasMessageContaining("nvd.api-key")
                    .hasMessageNotContaining("0f8fad5b");
        }

        @Test
        @DisplayName("임시 저장소 원격과 시험용 가짜 열쇠는 통과한다")
        void testValuesPass() {
            StandardEnvironment environment = environment("publish.remote", "origin");
            environment.getPropertySources().addFirst(new MapPropertySource("fakes", Map.of(
                    "summary.api-key", "summary-key-41c9e7", "nvd.api-key", "nvd-key-7f3a9c")));

            assertThatCode(() -> TestIsolationGuard.check(environment)).doesNotThrowAnyException();
        }

        private StandardEnvironment environment(String property, String value) {
            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource("case", Map.of(property, value)));
            return environment;
        }
    }
}
