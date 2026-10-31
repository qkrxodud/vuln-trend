package com.vulntrend.batch.application.export.dto;

import com.vulntrend.batch.domain.score.ScoreBreakdown;
import java.math.BigDecimal;

/**
 * score_breakdown (web/src/types/trend.ts ScoreBreakdown). 일곱 항은 소수 1자리, 화면 점수는 그 항들의 합을 정수로
 * 반올림한 값이다(R6, 확정 2026-10-31) — 보이는 항을 더하면 보이는 점수가 된다. 정렬은 저장된 trend_score 로 한다.
 */
public record ScoreBreakdownJson(BigDecimal kev, BigDecimal ransomware, BigDecimal epss, BigDecimal news,
        BigDecimal poc, BigDecimal cvss, BigDecimal decay) {

    public static ScoreBreakdownJson from(ScoreBreakdown breakdown) {
        ScoreBreakdown rounded = breakdown.rounded();
        return new ScoreBreakdownJson(rounded.getKev(), rounded.getRansomware(), rounded.getEpss(),
                rounded.getNews(), rounded.getPoc(), rounded.getCvss(), rounded.getDecay());
    }

    public static int scoreOf(ScoreBreakdown breakdown) {
        return breakdown.score().intValueExact();
    }
}
