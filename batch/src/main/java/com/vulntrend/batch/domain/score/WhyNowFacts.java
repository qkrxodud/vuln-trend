package com.vulntrend.batch.domain.score;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * "왜 지금?" 한 줄을 고르는 데 쓰는 사실들(기획서 6장 규칙 표, WBS 3.2).
 * 랜섬웨어는 KEV 에 오른 동안만 사실로 친다(트렌드 점수 3.1 의 (o) 와 같은 규칙).
 */
public class WhyNowFacts {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Getter
    private final boolean kev;
    private final boolean ransomware;
    private final int pocCount;
    private final BigDecimal epssPercentile;
    @Getter
    private final int mentionCount;
    private final BigDecimal cvss;

    @Builder(access = AccessLevel.PRIVATE)
    private WhyNowFacts(boolean kev, boolean ransomware, int pocCount, BigDecimal epssPercentile, int mentionCount,
            BigDecimal cvss) {
        this.kev = kev;
        this.ransomware = ransomware;
        this.pocCount = pocCount;
        this.epssPercentile = epssPercentile;
        this.mentionCount = mentionCount;
        this.cvss = cvss;
    }

    public static WhyNowFacts of(boolean kev, boolean ransomware, int pocCount, BigDecimal epssPercentile,
            int mentionCount, BigDecimal cvss) {
        requireCount("poc count", pocCount);
        requireCount("mention count", mentionCount);
        requirePercentile(epssPercentile);
        return WhyNowFacts.builder()
                .kev(kev)
                .ransomware(ransomware)
                .pocCount(pocCount)
                .epssPercentile(epssPercentile)
                .mentionCount(mentionCount)
                .cvss(cvss)
                .build();
    }

    public boolean hasPoc() {
        return pocCount > 0;
    }

    public boolean isRansomware() {
        return kev && ransomware;
    }

    public boolean isEpssAtLeast(BigDecimal percentile) {
        return Objects.nonNull(epssPercentile) && epssPercentile.compareTo(percentile) >= 0;
    }

    /** 상위 N% 의 N. 100 에서 백분위를 뺀 값을 반올림하되 0% 로는 적지 않는다(프론트 mock 과 같은 계산) */
    public int epssTopPercent() {
        BigDecimal top = BigDecimal.ONE.subtract(epssPercentile).multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP);
        return Math.max(1, top.intValueExact());
    }

    public Optional<BigDecimal> getCvss() {
        return Optional.ofNullable(cvss);
    }

    private static void requireCount(String name, int count) {
        if (count < 0) {
            throw new DomainValidationException(name + " must not be negative.");
        }
    }

    private static void requirePercentile(BigDecimal percentile) {
        boolean outOfRange = Objects.nonNull(percentile)
                && (percentile.signum() < 0 || percentile.compareTo(BigDecimal.ONE) > 0);
        if (outOfRange) {
            throw new DomainValidationException("EPSS percentile must be between 0 and 1.");
        }
    }
}
