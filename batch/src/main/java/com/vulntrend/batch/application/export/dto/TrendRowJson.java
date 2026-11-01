package com.vulntrend.batch.application.export.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** index.json·recent.json 의 한 줄 (web/src/types/trend.ts TrendRow). */
public record TrendRowJson(String id, String title, int score, ScoreBreakdownJson scoreBreakdown, String whyNow,
        boolean isKev, boolean kevRansomware, LocalDate kevDueDate, LocalDate kevAddedAt, OffsetDateTime publishedAt, int pocCount, BigDecimal epss,
        BigDecimal epssPercentile, BigDecimal cvss, String cvssVersion, String severity, String primaryProduct,
        String productFamily, List<String> attackTypes, String summaryWhat, List<String> fixVersions, String patchUrl) {
}
