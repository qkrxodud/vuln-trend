package com.vulntrend.batch.domain.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("\"왜 지금?\" 한 줄")
class WhyNowTemplateTest {

    private static final boolean KEV = true;
    private static final boolean NOT_KEV = false;
    private static final boolean RANSOMWARE = true;
    private static final boolean NO_RANSOMWARE = false;
    private static final int NO_POC = 0;
    private static final int NO_MENTION = 0;

    @Nested
    @DisplayName("CISA 악용 목록에 오른 CVE 는")
    class Kev {

        @Test
        @DisplayName("공개 PoC 도 있으면 실제 악용과 PoC 를 함께 알린다")
        void withPoc() {
            assertThat(phraseOf(KEV, RANSOMWARE, 2, "0.99", 5, "9.8")).isEqualTo("실제 악용 확인, 공개 PoC 존재");
        }

        @Test
        @DisplayName("PoC 가 없고 랜섬웨어에 쓰이면 랜섬웨어 캠페인을 알린다")
        void withRansomware() {
            assertThat(phraseOf(KEV, RANSOMWARE, NO_POC, "0.99", 5, "9.8")).isEqualTo("랜섬웨어 캠페인에 사용 중");
        }

        @Test
        @DisplayName("PoC 도 랜섬웨어도 없으면 등재 사실을 알린다")
        void listedOnly() {
            assertThat(phraseOf(KEV, NO_RANSOMWARE, NO_POC, "0.99", 5, "9.8")).isEqualTo("CISA 악용 목록 등재");
        }
    }

    @Nested
    @DisplayName("악용 목록에 없는 CVE 는")
    class NotKev {

        @Test
        @DisplayName("랜섬웨어 표시가 남아 있어도 랜섬웨어 캠페인을 알리지 않는다")
        void ransomwareNeedsKev() {
            assertThat(phraseOf(NOT_KEV, RANSOMWARE, NO_POC, "0.10", NO_MENTION, "7.5"))
                    .isEqualTo("CVSS 7.5, 최근 공개");
        }

        @Test
        @DisplayName("악용 확률이 상위 5% 안이면 PoC 와 뉴스보다 먼저 알린다")
        void epssBeforePocAndNews() {
            assertThat(phraseOf(NOT_KEV, NO_RANSOMWARE, 3, "0.96", 7, "9.8")).isEqualTo("30일 내 악용 확률 상위 4%");
        }

        @Test
        @DisplayName("악용 확률이 상위 5% 밖이고 공개 PoC 가 있으면 스캔 증가를 알린다")
        void pocOnly() {
            assertThat(phraseOf(NOT_KEV, NO_RANSOMWARE, 1, "0.949", 7, "9.8")).isEqualTo("공개 PoC 존재, 스캔 증가 가능");
        }

        @Test
        @DisplayName("PoC 가 없고 최근 7일 뉴스 언급이 3건 이상이면 언급 수를 알린다")
        void news() {
            assertThat(phraseOf(NOT_KEV, NO_RANSOMWARE, NO_POC, "0.5", 3, "9.8")).isEqualTo("최근 7일 보안 뉴스 3건 언급");
        }

        @Test
        @DisplayName("뉴스 언급이 2건이면 심각도 점수로 넘어간다")
        void twoMentionsAreNotEnough() {
            assertThat(phraseOf(NOT_KEV, NO_RANSOMWARE, NO_POC, "0.5", 2, "6.4")).isEqualTo("CVSS 6.4, 최근 공개");
        }

        @Test
        @DisplayName("악용 확률을 모르면 다음 규칙으로 넘어간다")
        void unknownEpss() {
            assertThat(phraseOf(NOT_KEV, NO_RANSOMWARE, NO_POC, null, NO_MENTION, "10")).isEqualTo("CVSS 10.0, 최근 공개");
        }

        @Test
        @DisplayName("아무 신호가 없고 심각도 점수도 아직 없으면 미평가로 적는다")
        void unrated() {
            assertThat(phraseOf(NOT_KEV, NO_RANSOMWARE, NO_POC, null, NO_MENTION, null))
                    .isEqualTo("CVSS 미평가, 최근 공개");
        }
    }

    @Nested
    @DisplayName("악용 확률 상위 N% 는")
    class TopPercent {

        @ParameterizedTest(name = "백분위 {0} 이면 상위 {1}%")
        @CsvSource({"0.95, 5", "0.955, 5", "0.96, 4", "0.985, 2", "0.97, 3", "0.99, 1"})
        @DisplayName("100 에서 백분위를 뺀 값을 반올림한다")
        void rounded(String percentile, int top) {
            assertThat(phraseOf(NOT_KEV, NO_RANSOMWARE, NO_POC, percentile, NO_MENTION, "9.8"))
                    .isEqualTo("30일 내 악용 확률 상위 " + top + "%");
        }

        @ParameterizedTest(name = "백분위 {0} 이어도 상위 1%")
        @CsvSource({"0.995", "0.999", "1"})
        @DisplayName("0% 로 적지 않고 가장 작게 1% 로 적는다")
        void atLeastOne(String percentile) {
            assertThat(phraseOf(NOT_KEV, NO_RANSOMWARE, NO_POC, percentile, NO_MENTION, "9.8"))
                    .isEqualTo("30일 내 악용 확률 상위 1%");
        }
    }

    @Nested
    @DisplayName("사실이 맞지 않으면 받지 않는다")
    class Invalid {

        @ParameterizedTest(name = "백분위 {0}")
        @CsvSource({"-0.01", "1.00001"})
        @DisplayName("악용 확률 백분위는 0 과 1 사이여야 한다")
        void percentileRange(String percentile) {
            assertThatThrownBy(() -> facts(NOT_KEV, NO_RANSOMWARE, NO_POC, percentile, NO_MENTION, "9.8"))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("공개 PoC 수는 음수일 수 없다")
        void negativePoc() {
            assertThatThrownBy(() -> facts(NOT_KEV, NO_RANSOMWARE, -1, "0.5", NO_MENTION, "9.8"))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("뉴스 언급 수는 음수일 수 없다")
        void negativeMention() {
            assertThatThrownBy(() -> facts(NOT_KEV, NO_RANSOMWARE, NO_POC, "0.5", -1, "9.8"))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    private static String phraseOf(boolean kev, boolean ransomware, int poc, String percentile, int mentions,
            String cvss) {
        return WhyNowTemplate.phraseOf(facts(kev, ransomware, poc, percentile, mentions, cvss));
    }

    private static WhyNowFacts facts(boolean kev, boolean ransomware, int poc, String percentile, int mentions,
            String cvss) {
        return WhyNowFacts.of(kev, ransomware, poc, decimal(percentile), mentions, decimal(cvss));
    }

    private static BigDecimal decimal(String text) {
        return Optional.ofNullable(text).map(BigDecimal::new).orElse(null);
    }
}
