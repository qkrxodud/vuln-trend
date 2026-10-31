package com.vulntrend.batch.domain.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("트렌드 점수의 항별 기여도")
class ScoreBreakdownTest {

    private static final BigDecimal KEV = new BigDecimal("50");
    private static final BigDecimal RANSOMWARE = new BigDecimal("10");
    private static final BigDecimal NONE = BigDecimal.ZERO;

    @Nested
    @DisplayName("항을 나눠 보면")
    class Terms {

        @Test
        @DisplayName("갓 발행된 CVE 는 시간 감쇠가 없고 네 항을 더한 값이 점수다")
        void freshHasNoDecay() {
            ScoreBreakdown breakdown = ScoreBreakdown.of(KEV, RANSOMWARE, new BigDecimal("27"), new BigDecimal("9.8"),
                    BigDecimal.ONE);

            assertThat(breakdown.getDecay()).isEqualByComparingTo("0");
            assertThat(breakdown.total()).isEqualByComparingTo("96.8");
        }

        @Test
        @DisplayName("30일이 지나면 시간 감쇠가 점수의 절반을 덜어 낸다")
        void thirtyDaysHalves() {
            ScoreBreakdown breakdown = ScoreBreakdown.of(KEV, RANSOMWARE, new BigDecimal("27"), new BigDecimal("9.8"),
                    new BigDecimal("0.5"));

            assertThat(breakdown.getDecay()).isEqualByComparingTo("-48.4");
            assertThat(breakdown.total()).isEqualByComparingTo("48.4");
        }

        @Test
        @DisplayName("뉴스 언급과 공개 PoC 는 2단계 점수식 항이라 지금은 0 이다")
        void newsAndPocAreZero() {
            ScoreBreakdown breakdown = ScoreBreakdown.of(KEV, NONE, NONE, NONE, BigDecimal.ONE);

            assertThat(breakdown.getNews()).isEqualByComparingTo("0");
            assertThat(breakdown.getPoc()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("일곱 항을 모두 더하면 점수가 된다")
        void sevenTermsAddUp() {
            ScoreBreakdown breakdown = ScoreBreakdown.of(KEV, NONE, new BigDecimal("28.293"), new BigDecimal("9.8"),
                    new BigDecimal("0.757858283255199"));

            BigDecimal sum = breakdown.getKev().add(breakdown.getRansomware()).add(breakdown.getEpss())
                    .add(breakdown.getNews()).add(breakdown.getPoc()).add(breakdown.getCvss()).add(breakdown.getDecay());
            assertThat(sum).isEqualByComparingTo(breakdown.total());
        }
    }

    @Nested
    @DisplayName("사이트에 보일 때")
    class ForSite {

        // 12일 지난 KEV CVE, EPSS 0.94310, CVSS 9.8 — 프론트 목업(generate-mock-data.ts) 계산으로 67 이 나온다
        private final ScoreBreakdown twelveDaysOld = ScoreBreakdown.of(KEV, NONE, new BigDecimal("28.293"),
                new BigDecimal("9.8"), new BigDecimal("0.757858283255199"));

        @Test
        @DisplayName("항은 소수 첫째 자리로 반올림한다")
        void termsToOneDecimal() {
            ScoreBreakdown rounded = twelveDaysOld.rounded();

            assertThat(rounded.getEpss()).isEqualByComparingTo("28.3");
            assertThat(rounded.getCvss()).isEqualByComparingTo("9.8");
            assertThat(rounded.getDecay()).isEqualByComparingTo("-21.3");
        }

        @Test
        @DisplayName("점수는 반올림한 항을 더해 정수로 반올림한다 — 보이는 항을 더하면 보이는 점수가 된다")
        void scoreFromRoundedTerms() {
            assertThat(twelveDaysOld.score()).isEqualByComparingTo("67");
        }
    }

    @Nested
    @DisplayName("받을 수 없는 값")
    class Refusing {

        @ParameterizedTest(name = "감쇠 비율 {0}")
        @ValueSource(strings = {"-0.1", "1.0001"})
        @DisplayName("시간 감쇠 비율이 0 이상 1 이하가 아니면 받지 않는다")
        void decayFactorOutOfRange(String factor) {
            assertThatThrownBy(() -> ScoreBreakdown.of(KEV, NONE, NONE, NONE, new BigDecimal(factor)))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("아주 오래돼 감쇠 비율이 0 이 되면 점수가 모두 빠진다")
        void ancientDecaysToZero() {
            assertThat(ScoreBreakdown.of(KEV, NONE, NONE, NONE, BigDecimal.ZERO).total()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("항이 비어 있거나 0 보다 작으면 받지 않는다")
        void badTermIsRefused() {
            assertThatThrownBy(() -> ScoreBreakdown.of(null, NONE, NONE, NONE, BigDecimal.ONE))
                    .isInstanceOf(DomainValidationException.class);
            assertThatThrownBy(() -> ScoreBreakdown.of(KEV, NONE, new BigDecimal("-1"), NONE, BigDecimal.ONE))
                    .isInstanceOf(DomainValidationException.class);
            assertThatThrownBy(() -> ScoreBreakdown.of(KEV, NONE, NONE, NONE, null))
                    .isInstanceOf(DomainValidationException.class);
        }
    }
}
