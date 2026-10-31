package com.vulntrend.batch.domain.export.dto;

import com.vulntrend.batch.domain.vulnerability.CveId;

/** 검색 한 건의 저장 값(search-index.json, WBS 4.2). title·product 는 비어 있을 수 있다 */
public record SearchListing(CveId id, String title, String product) {
}
