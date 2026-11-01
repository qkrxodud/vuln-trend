package com.vulntrend.batch.domain.export.dto;

/** 분류 하나의 건수(categories.json): 최근 7일·30일 발행 수와 KEV 전체 수 */
public record CategoryCount(int count7d, int count30d, int kevCount) {
}
