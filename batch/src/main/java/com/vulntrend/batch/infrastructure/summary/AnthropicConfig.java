package com.vulntrend.batch.infrastructure.summary;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.summary.Summarizer;
import com.vulntrend.batch.global.config.SummaryProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Claude 클라이언트(Anthropic 공식 Java SDK). 요약 열쇠와 모델 이름이 있을 때만 만든다 — 없으면 요약 단계가 건너뛴다(비치명).
 * fromEnv() 를 쓰지 않고 SummaryProperties 의 열쇠만 넘긴다. 429·5xx 는 SDK 가 기본 2번 다시 부른다.
 */
@Configuration
@Conditional(SummaryConfiguredCondition.class)
public class AnthropicConfig {

    @Bean
    public AnthropicClient summaryClient(SummaryProperties properties) {
        return AnthropicOkHttpClient.builder().apiKey(properties.getApiKey()).build();
    }

    @Bean
    public Summarizer summarizer(AnthropicClient summaryClient, SummaryProperties properties,
            ObjectMapper objectMapper) {
        return new AnthropicSummarizer(summaryClient, properties, objectMapper);
    }
}
