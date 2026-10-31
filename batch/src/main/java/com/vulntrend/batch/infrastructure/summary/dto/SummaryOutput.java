package com.vulntrend.batch.infrastructure.summary.dto;

/** Claude 가 구조화 출력으로 돌려주는 요약 세 칸. 모양은 AnthropicSummarizer 의 스키마가 정한다 */
public record SummaryOutput(String what, String who, String action) {
}
