package com.vulntrend.batch.domain.export.dto;

/** 참고 링크 저장 값(reference_link 한 줄, WBS 4.2 cve/*.json). source 는 nvd·vendor·news·poc, title 은 비어 있을 수 있다 */
public record ReferenceListing(String url, String source, String title) {
}
