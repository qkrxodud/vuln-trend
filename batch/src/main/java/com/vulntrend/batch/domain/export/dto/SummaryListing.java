package com.vulntrend.batch.domain.export.dto;

import java.time.Instant;

/** 한국어 요약 저장 값(summary 한 줄, WBS 4.2 cve/*.json) */
public record SummaryListing(String what, String who, String action, String model, Instant generatedAt) {
}
