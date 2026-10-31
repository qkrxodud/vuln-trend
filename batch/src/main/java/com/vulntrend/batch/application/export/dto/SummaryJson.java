package com.vulntrend.batch.application.export.dto;

import java.time.OffsetDateTime;

/** cve/*.json summary (web/src/types/cve.ts Summary). */
public record SummaryJson(String what, String who, String action, String model, OffsetDateTime generatedAt) {
}
