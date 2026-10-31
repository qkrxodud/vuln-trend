package com.vulntrend.batch.infrastructure.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.score.ScoreBreakdown;
import com.vulntrend.batch.infrastructure.export.dto.ScoreBreakdownJson;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사이트에 싣는 점수 구성")
class ScoreBreakdownJsonTest {

    // 12일 지난 KEV CVE, EPSS 0.94310, CVSS 9.8 — 화면 점수 67 (ScoreBreakdownTest 와 같은 예)
    private static final ScoreBreakdown TWELVE_DAYS_OLD = ScoreBreakdown.of(new BigDecimal("50"), BigDecimal.ZERO,
            new BigDecimal("28.293"), new BigDecimal("9.8"), new BigDecimal("0.757858283255199"));

    @Test
    @DisplayName("일곱 항을 소수 첫째 자리로 싣는다")
    void sevenTermsToOneDecimal() {
        ScoreBreakdownJson json = ScoreBreakdownJson.from(TWELVE_DAYS_OLD);

        assertThat(json.kev()).isEqualTo(new BigDecimal("50.0"));
        assertThat(json.ransomware()).isEqualTo(new BigDecimal("0.0"));
        assertThat(json.epss()).isEqualTo(new BigDecimal("28.3"));
        assertThat(json.news()).isEqualTo(new BigDecimal("0.0"));
        assertThat(json.poc()).isEqualTo(new BigDecimal("0.0"));
        assertThat(json.cvss()).isEqualTo(new BigDecimal("9.8"));
        assertThat(json.decay()).isEqualTo(new BigDecimal("-21.3"));
    }

    @Test
    @DisplayName("실린 항을 더해 반올림하면 화면 점수가 된다")
    void termsAddUpToScore() {
        ScoreBreakdownJson json = ScoreBreakdownJson.from(TWELVE_DAYS_OLD);
        BigDecimal sum = json.kev().add(json.ransomware()).add(json.epss()).add(json.news()).add(json.poc())
                .add(json.cvss()).add(json.decay());

        assertThat(sum.setScale(0, RoundingMode.HALF_UP)).isEqualTo(TWELVE_DAYS_OLD.score());
        assertThat(ScoreBreakdownJson.scoreOf(TWELVE_DAYS_OLD)).isEqualTo(67);
    }
}
