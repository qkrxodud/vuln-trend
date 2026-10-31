package com.vulntrend.batch.application.export.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** stats.json (web/src/types/stats.ts Stats). */
public record StatsJson(List<DailyCountJson> daily, @JsonProperty("severity_7d") SeverityDistributionJson severity7d) {
}
