package com.vulntrend.batch.infrastructure.summary.dto;

/** 요약 모델이 구조화 출력으로 돌려주는 요약 세 칸. 모양은 OpenAiSummarizer 의 스키마가 정한다 */
public record SummaryOutput(String what, String who, String action) {
}
