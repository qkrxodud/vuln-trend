package com.vulntrend.batch.application.export.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/** stats.json daily[] (web/src/types/stats.ts DailyCount). */
public record DailyCountJson(LocalDate date, @JsonProperty("new") int newCount, int critical) {
}
