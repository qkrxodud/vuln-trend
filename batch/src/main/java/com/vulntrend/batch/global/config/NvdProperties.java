package com.vulntrend.batch.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NVD 설정(기획서 3장 NVD 주의사항). apiKey 값은 어떤 로그에도 남기지 않는다. 비어 있으면 NvdClient 가 시작을 거부한다.
 * requestInterval 은 열쇠가 있을 때 한도(30초에 50번)에 맞춘 0.6초. maxWindow 는 NVD 한 번 요청의 구간 상한 120일.
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "nvd")
public class NvdProperties {

    private final String apiKey;
    private final String baseUrl;
    private final int resultsPerPage;
    private final Duration maxWindow;
    private final Duration requestInterval;
    private final int maxAttempts;
    private final Duration retryBackoff;
}
