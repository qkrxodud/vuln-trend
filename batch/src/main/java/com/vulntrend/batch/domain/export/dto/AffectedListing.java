package com.vulntrend.batch.domain.export.dto;

/** 영향 제품 저장 값(affected_product 한 줄 + 표시 이름, WBS 4.2 cve/*.json). 표시 이름·slug 등은 비어 있을 수 있다 */
public record AffectedListing(String vendor, String product, String displayName, String slug, String ecosystem,
        String vulnerableRange, String fixedVersion) {
}
