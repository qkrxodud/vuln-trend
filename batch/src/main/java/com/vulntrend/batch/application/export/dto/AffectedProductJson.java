package com.vulntrend.batch.application.export.dto;

import java.util.List;

/** cve/*.json affected[] (web/src/types/cve.ts AffectedProduct). */
public record AffectedProductJson(String vendor, String product, String displayName, String slug, String ecosystem,
        String vulnerableRange, String fixedVersion, List<String> fixVersions) {
}
