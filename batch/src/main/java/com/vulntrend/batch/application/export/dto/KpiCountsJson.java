package com.vulntrend.batch.application.export.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** meta.json today·yesterday (web/src/types/meta.ts KpiCounts·YesterdayKpiCounts). */
public record KpiCountsJson(@JsonProperty("new") int newCount, int kevNew, int critical, Integer highRisk) {
}
