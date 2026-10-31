package com.vulntrend.batch.domain.enrichment;

import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 한 CVE 의 악용 확률(EPSS, 30일 안에 악용될 확률)과 그 순위(percentile). 둘 다 0~1 이다(기획서 3장, P10).
 * vulnerability.epss_score·epss_percentile 이 numeric(6,5) 라서 소수 다섯째 자리로 맞춘다.
 */
@Getter
public class EpssScore {

    private static final int SCALE = 5;

    private final CveId cveId;
    private final BigDecimal score;
    private final BigDecimal percentile;

    @Builder(access = AccessLevel.PRIVATE)
    private EpssScore(CveId cveId, BigDecimal score, BigDecimal percentile) {
        this.cveId = cveId;
        this.score = score;
        this.percentile = percentile;
    }

    public static EpssScore of(CveId cveId, BigDecimal score, BigDecimal percentile) {
        if (Objects.isNull(cveId)) {
            throw new DomainValidationException("An EPSS score needs a CVE id.");
        }
        return EpssScore.builder()
                .cveId(cveId)
                .score(probability(score, "score"))
                .percentile(probability(percentile, "percentile"))
                .build();
    }

    private static BigDecimal probability(BigDecimal value, String name) {
        if (isNotProbability(value)) {
            throw new DomainValidationException("An EPSS " + name + " must be between 0 and 1.");
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static boolean isNotProbability(BigDecimal value) {
        return Objects.isNull(value) || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0;
    }
}
