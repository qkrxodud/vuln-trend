package com.vulntrend.batch.application.export.dto;

/** stats.json severity_7d (web/src/types/stats.ts SeverityDistribution). */
public record SeverityDistributionJson(int critical, int high, int medium, int low, int unrated) {
}
