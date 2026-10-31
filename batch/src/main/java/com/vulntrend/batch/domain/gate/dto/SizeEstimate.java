package com.vulntrend.batch.domain.gate.dto;

/** search-index 를 그 범위로 썼을 때의 크기: 범위 이름·건수·들여쓰기 바이트·줄바꿈 없는 바이트 */
public record SizeEstimate(String scope, int entries, long prettyBytes, long compactBytes) {
}
