package com.vulntrend.batch.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 프론트 EPSS 표시 규칙의 사본(web/src/lib/list-model.ts formatEpssPercent·formatEpssRank, 2026-11-02 기준).
 * 프론트는 JSON 숫자를 JS 실수(double)로 읽고 toFixed 로 자른다. 같은 문자열이 나오게 여기서도 double 로 계산한다.
 * toFixed 는 실수의 정확한 이진값에서 가까운 쪽(같으면 큰 쪽)을 고른다 — new BigDecimal(double) + HALF_UP 과 같다.
 * 프론트 규칙이 바뀌면 이 사본도 같이 바꾼다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FrontEpssFormat {

    /** JSON 에 쓴 값을 프론트가 보이는 문자열로 */
    public static String percent(BigDecimal written) {
        double percent = written.doubleValue() * 100;
        if (percent >= 99.95) {
            return "100%";
        }
        if (percent >= 10) {
            return toFixed(percent, 0) + "%";
        }
        if (percent >= 1) {
            return toFixed(percent, 1) + "%";
        }
        if (percent >= 0.01) {
            return toFixed(percent, 2) + "%";
        }
        return "<0.01%";
    }

    public static String rank(BigDecimal written) {
        double top = (1 - written.doubleValue()) * 100;
        if (top < 1) {
            return "상위 1% 안";
        }
        return "상위 " + (long) Math.ceil(top - 1e-9) + "%";
    }

    private static String toFixed(double value, int digits) {
        return new BigDecimal(value).setScale(digits, RoundingMode.HALF_UP).toPlainString();
    }
}
