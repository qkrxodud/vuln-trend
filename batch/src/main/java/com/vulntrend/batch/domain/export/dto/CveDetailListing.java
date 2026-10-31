package com.vulntrend.batch.domain.export.dto;

import com.vulntrend.batch.domain.score.dto.TrendListing;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 상세 1건을 그리는 데 필요한 저장 값(cve/*.json, WBS 4.2): 목록 줄 전부 + 원문 설명·시각·요약·영향 제품·참고 링크.
 * description·lastModifiedAt·kevAddedAt·summary 는 비어 있을 수 있다.
 */
public record CveDetailListing(TrendListing row, String description, Instant lastModifiedAt, LocalDate kevAddedAt,
        SummaryListing summary, List<AffectedListing> affected, List<ReferenceListing> references) {
}
