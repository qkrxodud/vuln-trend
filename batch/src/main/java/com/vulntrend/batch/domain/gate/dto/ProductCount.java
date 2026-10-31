package com.vulntrend.batch.domain.gate.dto;

/** 대표 제품 이름과 그 이름이 붙은 CVE 수 */
public record ProductCount(String name, int count) {
}
