package com.vulntrend.batch.domain.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.support.FrontEpssFormat;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("사이트에 쓰는 EPSS 자릿수")
class EpssShownTest {

    @Nested
    @DisplayName("악용 확률")
    class Probability {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"0.12345, 0.12", "0.5, 0.50", "0.012345, 0.012", "0.0987, 0.099", "0.00123, 0.0012",
                "0.00004, 0.0000"})
        @DisplayName("화면이 보이는 자리까지만 반올림해 쓴다(10% 이상 정수, 1% 이상 소수 1자리, 그 아래 2자리)")
        void roundsToShownDigits(String raw, String written) {
            assertThat(EpssShown.probability(new BigDecimal(raw))).isEqualTo(new BigDecimal(written));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"0.00004", "0.0001", "0.00012", "0.00049", "0.0012", "0.00994", "0.01", "0.01234", "0.0987",
                "0.09949", "0.1", "0.12345", "0.5", "0.99949", "0.9996", "1"})
        @DisplayName("반올림 자리 다음이 정확히 5가 아니면 화면에 보이는 글자가 지금과 같다")
        void sameTextOnScreen(String raw) {
            BigDecimal epss = new BigDecimal(raw);

            assertThat(FrontEpssFormat.percent(EpssShown.probability(epss))).isEqualTo(FrontEpssFormat.percent(epss));
        }

        @ParameterizedTest(name = "{0}: 지금 {1} → {2}")
        @CsvSource({"0.00295, 0.29%, 0.30%", "0.0195, 1.9%, 2.0%", "0.0725, 7.2%, 7.3%", "0.003, 0.30%, 0.30%"})
        @DisplayName("정확히 반인 값은 화면에 바르게 반올림한 숫자로 보인다")
        void halfShowsRoundedUp(String raw, String shownNow, String shownAfter) {
            BigDecimal epss = new BigDecimal(raw);

            assertThat(FrontEpssFormat.percent(epss)).isEqualTo(shownNow);
            assertThat(FrontEpssFormat.percent(EpssShown.probability(epss))).isEqualTo(shownAfter);
        }

        @ParameterizedTest(name = "{0}: 지금 {1} → {2}")
        @CsvSource({"0.00998, 1.00%, 1.0%", "0.0999, 10.0%, 10%"})
        @DisplayName("반올림으로 윗단계에 닿는 값은 같은 숫자를 윗단계 형식으로 보인다")
        void promotedShowsNextFormat(String raw, String shownNow, String shownAfter) {
            BigDecimal epss = new BigDecimal(raw);

            assertThat(FrontEpssFormat.percent(epss)).isEqualTo(shownNow);
            assertThat(FrontEpssFormat.percent(EpssShown.probability(epss))).isEqualTo(shownAfter);
        }
    }

    @Nested
    @DisplayName("악용 확률 순위")
    class Percentile {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"0.987, 0.98", "0.5, 0.50", "0.99, 0.99", "0.9905, 0.991", "0.99001, 0.991", "0.99999, 1.000",
                "1, 1.000"})
        @DisplayName("상위 1% 밖은 소수 2자리로 내리고, 상위 1% 안은 소수 3자리로 올려 쓴다")
        void cutsToShownBucket(String raw, String written) {
            assertThat(EpssShown.percentile(new BigDecimal(raw))).isEqualTo(new BigDecimal(written));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"0", "0.00012", "0.5", "0.97", "0.975", "0.98", "0.985", "0.987", "0.98999", "0.99", "0.99001",
                "0.9905", "0.995", "0.99999", "1"})
        @DisplayName("어느 값이든 화면에 보이는 \"상위 N%\" 가 지금과 같다")
        void sameRankOnScreen(String raw) {
            BigDecimal percentile = new BigDecimal(raw);

            assertThat(FrontEpssFormat.rank(EpssShown.percentile(percentile)))
                    .isEqualTo(FrontEpssFormat.rank(percentile));
        }
    }

    @Test
    @DisplayName("반올림한 확률을 프론트가 다시 읽어도 이진 오차로 한 칸 내려가지 않는다")
    void roundedValueStaysOnScreen() {
        assertThat(FrontEpssFormat.percent(EpssShown.probability(new BigDecimal("0.00295")))).isEqualTo("0.30%");
    }
}
