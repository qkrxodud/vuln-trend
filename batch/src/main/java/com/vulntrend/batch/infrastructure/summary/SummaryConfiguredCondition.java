package com.vulntrend.batch.infrastructure.summary;

import com.vulntrend.batch.global.config.SummaryProperties;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 요약 열쇠와 모델 이름이 모두 있을 때만 요약 클라이언트(OpenAI)를 만든다. 열쇠가 없으면 클라이언트 자체가 없어서
 * SDK 가 OPENAI_API_KEY 같은 환경 변수를 대신 찾아 쓰는 일이 없다(열쇠를 읽는 길은 SummaryProperties 하나).
 */
public class SummaryConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        SummaryProperties properties = new SummaryProperties(0, 0,
                context.getEnvironment().getProperty("summary.model"),
                context.getEnvironment().getProperty("summary.api-key"));
        return properties.isConfigured();
    }
}
