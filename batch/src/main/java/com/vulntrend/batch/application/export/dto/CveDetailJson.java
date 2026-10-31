package com.vulntrend.batch.application.export.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** cve/CVE-*.json (web/src/types/cve.ts CveDetail = TrendRow 필드 전부 + 상세 전용 필드). 목록 줄은 펼쳐서 앞에 싣는다 */
public record CveDetailJson(@JsonUnwrapped TrendRowJson row, String description, OffsetDateTime publishedAt,
        OffsetDateTime lastModifiedAt, LocalDate kevAddedAt, SummaryJson summary, List<AffectedProductJson> affected,
        List<ReferenceLinkJson> references) {
}
