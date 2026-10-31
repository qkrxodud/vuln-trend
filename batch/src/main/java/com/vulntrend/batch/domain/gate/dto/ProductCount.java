package com.vulntrend.batch.domain.gate.dto;

/** 대표 제품 하나와 그 제품이 붙은 CVE 수. vendor 는 CPE 제조사(없으면 KEV 제조사), 둘 다 모르면 비어 있다 */
public record ProductCount(String vendor, String name, int count) {
}
