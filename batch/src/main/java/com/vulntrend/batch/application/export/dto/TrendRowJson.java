package com.vulntrend.batch.application.export.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** index.json·recent.json 의 한 줄 (web/src/types/trend.ts TrendRow). */
public record TrendRowJson(String id, String title, int score, ScoreBreakdownJson scoreBreakdown, String whyNow,
        boolean isKev, boolean kevRansomware, LocalDate kevDueDate, LocalDate kevAddedAt, int pocCount, BigDecimal epss,
        BigDecimal epssPercentile, BigDecimal cvss, String cvssVersion, String severity, String primaryProduct,
        String summaryWhat) {
}
