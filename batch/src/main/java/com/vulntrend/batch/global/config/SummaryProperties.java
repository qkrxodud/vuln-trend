package com.vulntrend.batch.global.config;

import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 한국어 요약 설정(기획서 6장 요약, WBS 6.1). dailyLimit = 하루 요약 상한(기본 20).
 * model = 요약에 쓰는 모델 이름. summary 의 model 칸에 그대로 남긴다(P9). apiKey 값은 어떤 로그에도 남기지 않는다.
 * 요약은 비치명 단계라 열쇠나 모델 이름이 비어 있어도 시작을 거부하지 않는다 — 요약 단계만 건너뛴다(NVD 열쇠와 반대).
 * 모델 제공자(Anthropic·OpenAI 등)는 결정 대기: 고르면 그 제공자의 Spring AI 모듈을 붙이고 이 두 값을 넘긴다.
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "summary")
public class SummaryProperties {

    private final int dailyLimit;
    private final String model;
    private final String apiKey;

    public boolean isNotConfigured() {
        return Stream.of(model, apiKey).anyMatch(Predicate.not(StringUtils::hasText));
    }
}
