package com.vulntrend.batch.infrastructure.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.vulntrend.batch.domain.summary.Summarizer;
import com.vulntrend.batch.global.config.SummaryProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 요약 클라이언트(OpenAI 공식 Java SDK, 사용자 결정 2026-11-01). 요약 열쇠와 모델 이름이 있을 때만 만든다 — 없으면 요약 단계가 건너뛴다(비치명).
 * fromEnv() 를 쓰지 않는다: SDK 4.95.0 에서 OPENAI_API_KEY·OPENAI_BASE_URL·OPENAI_ORG_ID 를 읽는 곳은 fromEnv() 뿐이다(javap 로 확인).
 * 그래서 열쇠는 SummaryProperties 에서만 온다. 429·5xx 는 SDK 가 기본 2번 다시 부른다.
 */
@Configuration
@Conditional(SummaryConfiguredCondition.class)
public class OpenAiConfig {

    @Bean
    public OpenAIClient summaryClient(SummaryProperties properties) {
        return OpenAIOkHttpClient.builder().apiKey(properties.getApiKey()).build();
    }

    @Bean
    public Summarizer summarizer(OpenAIClient summaryClient, SummaryProperties properties, ObjectMapper objectMapper) {
        return new OpenAiSummarizer(summaryClient, properties, objectMapper);
    }
}
