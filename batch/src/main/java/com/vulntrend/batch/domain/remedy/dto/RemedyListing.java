package com.vulntrend.batch.domain.remedy.dto;

import java.util.List;

/**
 * 조치 정보를 만드는 데 필요한 저장 값(사용자 피드백 "조치 방법이 없다", 승인 2026-11-01). 출력이 읽어 조치 필드로 바꾼다.
 * - primaryRanges: 대표 제품에 해당하는 영향 제품들의 취약 범위(제조사·제품 순, 범위가 없는 줄은 빠짐)
 * - nvdReferences: NVD 원본의 참고 링크(원본 순서)
 * - kevRequiredAction: KEV 원본의 requiredAction 원문. 없으면 null
 */
public record RemedyListing(List<String> primaryRanges, List<TaggedLink> nvdReferences, String kevRequiredAction) {
}
