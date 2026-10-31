package com.vulntrend.batch.domain.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("악용 확률(EPSS)")
class EpssScoreTest {

    private static final CveId LOG4SHELL = CveId.from("CVE-2021-44228");

    @Test
    @DisplayName("악용 확률과 악용 확률 순위를 FIRST 가 준 자릿수 그대로 담는다")
    void keepsBothValues() {
        EpssScore score = EpssScore.of(LOG4SHELL, new BigDecimal("0.99999"), new BigDecimal("1.0"));

        assertThat(score.getCveId()).isEqualTo(LOG4SHELL);
        assertThat(score.getScore()).isEqualByComparingTo("0.99999");
        assertThat(score.getPercentile()).isEqualByComparingTo("1");
    }

    @ParameterizedTest(name = "확률 {0}, 순위 {1}")
    @CsvSource({"-0.00001, 0.5", "1.00001, 0.5", "0.5, -0.1", "0.5, 1.5"})
    @DisplayName("확률이나 순위가 0 과 1 사이가 아니면 받지 않는다")
    void outOfRangeIsRefused(String score, String percentile) {
        assertThatThrownBy(() -> EpssScore.of(LOG4SHELL, new BigDecimal(score), new BigDecimal(percentile)))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("CVE 번호나 확률·순위가 비어 있으면 받지 않는다")
    void missingIsRefused() {
        assertThatThrownBy(() -> EpssScore.of(null, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> EpssScore.of(LOG4SHELL, null, BigDecimal.ONE))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> EpssScore.of(LOG4SHELL, BigDecimal.ONE, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("저장 칸의 소수 다섯째 자리보다 길면 다섯째 자리에서 반올림한다")
    void roundsToFiveDigits() {
        EpssScore score = EpssScore.of(LOG4SHELL, new BigDecimal("0.123456"), new BigDecimal("0.987654"));

        assertThat(score.getScore()).isEqualByComparingTo("0.12346");
        assertThat(score.getPercentile()).isEqualByComparingTo("0.98765");
    }
}
