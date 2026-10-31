package com.vulntrend.batch.application.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.global.config.SummaryProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("한국어 요약")
@ExtendWith(OutputCaptureExtension.class)
class SummaryServiceTest {

    private static final String KEY = "summary-key-41c9e7";

    @Nested
    @DisplayName("요약 열쇠나 모델 이름이 없으면")
    class NotConfigured {

        @Test
        @DisplayName("요약을 하나도 만들지 않고 이번 실행은 요약 없이 넘어간다")
        void skipsWithoutKey(CapturedOutput output) {
            int generated = service(new SummaryProperties(20, "some-model", "")).summarizeTop();

            assertThat(generated).isZero();
            assertThat(output).contains("Korean summary is skipped");
        }

        @Test
        @DisplayName("열쇠가 있어도 모델 이름이 없으면 요약 없이 넘어간다")
        void skipsWithoutModel(CapturedOutput output) {
            service(new SummaryProperties(20, "", KEY)).summarizeTop();

            assertThat(output).contains("Korean summary is skipped");
        }
    }

    @Nested
    @DisplayName("요약 열쇠와 모델 이름이 모두 있으면")
    class Configured {

        @Test
        @DisplayName("요약 단계를 건너뛰지 않는다")
        void doesNotSkip(CapturedOutput output) {
            service(new SummaryProperties(20, "some-model", KEY)).summarizeTop();

            assertThat(output).doesNotContain("Korean summary is skipped");
            assertThat(output).contains("some-model");
        }

        @Test
        @DisplayName("요약 열쇠 값은 실행 기록에 남지 않는다")
        void keyNeverLogged(CapturedOutput output) {
            service(new SummaryProperties(20, "some-model", KEY)).summarizeTop();

            assertThat(output).doesNotContain(KEY);
        }
    }

    private static SummaryService service(SummaryProperties properties) {
        return new SummaryService(properties);
    }
}
