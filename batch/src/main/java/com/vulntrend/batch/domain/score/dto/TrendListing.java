package com.vulntrend.batch.domain.score.dto;

import com.vulntrend.batch.domain.score.ScoreBreakdown;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 트렌드 목록 한 줄을 그리는 데 필요한 저장 값(WBS 4.2 index·recent). 저장소가 읽어 넘기고 출력이 JSON 모양으로 바꾼다.
 * 점수 구성은 같은 실행 시각으로 계산한 값이다. 비어 있을 수 있는 값: title·kevDueDate·epss·epssPercentile·cvss·cvssVersion·
 * primaryProduct·summaryWhat·publishedAt.
 */
public record TrendListing(CveId id, String title, ScoreBreakdown breakdown, boolean kev, boolean kevRansomware,
        LocalDate kevDueDate, int pocCount, int mentionCount, BigDecimal epss, BigDecimal epssPercentile,
        BigDecimal cvss, String cvssVersion, Severity severity, String primaryProduct, String summaryWhat,
        Instant publishedAt) {
}
