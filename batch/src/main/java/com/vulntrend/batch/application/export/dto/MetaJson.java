package com.vulntrend.batch.application.export.dto;

import java.time.OffsetDateTime;

/** meta.json (web/src/types/meta.ts Meta). */
public record MetaJson(OffsetDateTime updatedAt, String version, KpiCountsJson today, KpiCountsJson yesterday) {
}
