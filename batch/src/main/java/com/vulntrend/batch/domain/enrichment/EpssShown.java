package com.vulntrend.batch.domain.enrichment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 사이트 데이터 파일에 쓰는 EPSS 자릿수(S4, 오케스트레이터 승인 2026-11-02, 계약 G1 값 자릿수만 바꿈).
 * EPSS 는 매일 소수 5자리가 조금씩 바뀌어 상세 파일 거의 전부가 다시 쓰였다. 화면이 보이는 자리까지만 쓰면 보이는 글자가 바뀔 때만 파일이 바뀐다.
 * - 확률: 프론트 formatEpssPercent 와 같은 자리에서 HALF_UP — 0.1 이상 소수 2자리, 0.01 이상 3자리, 그 아래 4자리.
 *   정확히 반인 값(0.00295 등, 2026-11-02 실측 398,815건 중 12,964건)은 지금 화면이 이진 오차로 내려 보이던 것을 바른 반올림으로 보이게 된다
 * - 순위: 프론트 formatEpssRank 와 같은 칸 — 0.99 이하는 소수 2자리 내림, 0.99 초과(상위 1% 안)는 3자리 올림. 같은 실측에서 화면이 바뀌는 값 0건
 * 점수식(EPSS × 30)은 원래 값으로 계산한다. 여기 자릿수는 파일에 쓰는 값에만 쓴다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EpssShown {

    private static final BigDecimal WHOLE_PERCENT = new BigDecimal("0.1");
    private static final BigDecimal ONE_PERCENT = new BigDecimal("0.01");
    private static final BigDecimal TOP_ONE_PERCENT = new BigDecimal("0.99");

    public static BigDecimal probability(BigDecimal epss) {
        if (epss.compareTo(WHOLE_PERCENT) >= 0) {
            return epss.setScale(2, RoundingMode.HALF_UP);
        }
        if (epss.compareTo(ONE_PERCENT) >= 0) {
            return epss.setScale(3, RoundingMode.HALF_UP);
        }
        return epss.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentile(BigDecimal percentile) {
        if (percentile.compareTo(TOP_ONE_PERCENT) > 0) {
            return percentile.setScale(3, RoundingMode.CEILING);
        }
        return percentile.setScale(2, RoundingMode.FLOOR);
    }
}
