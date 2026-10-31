package com.vulntrend.batch.application.export.dto;

/** cve/*.json references[] (web/src/types/cve.ts ReferenceLink). */
public record ReferenceLinkJson(String url, String source, String title) {
}
