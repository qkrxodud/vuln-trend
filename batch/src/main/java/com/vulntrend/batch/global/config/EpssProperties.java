package com.vulntrend.batch.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** FIRST EPSS 설정(백엔드 B 요청 2026-10-31, WBS 2.2). url 은 전체 점수 gzip CSV */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "epss")
public class EpssProperties {

    private final String url;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final Duration requestTimeout;
}
