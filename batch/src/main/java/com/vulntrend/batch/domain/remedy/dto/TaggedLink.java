package com.vulntrend.batch.domain.remedy.dto;

import java.util.List;

/** NVD 원본(vulnerability_raw nvd)의 참고 링크 한 줄: 주소와 NVD 태그 원문(없으면 빈 목록) */
public record TaggedLink(String url, List<String> tags) {
}
