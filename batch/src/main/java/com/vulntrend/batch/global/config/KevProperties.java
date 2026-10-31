package com.vulntrend.batch.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CISA KEV 설정(백엔드 B 요청 2026-10-31, WBS 2.1). url 은 KEV JSON 파일 하나 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "kev")
public class KevProperties {

    private final String url;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final Duration requestTimeout;
}
