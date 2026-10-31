package com.vulntrend.batch.infrastructure.export.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 파일 쓰기 시험용 문서. 실제 계약 문서(4.2)와 같은 종류의 값을 고루 담는다 */
public record ExportSampleJson(String id, int score, BigDecimal epss, BigDecimal epssPercentile, LocalDate kevDueDate,
        OffsetDateTime updatedAt, String primaryProduct, boolean isKev, List<String> tags,
        Map<String, Integer> counts) {
}
