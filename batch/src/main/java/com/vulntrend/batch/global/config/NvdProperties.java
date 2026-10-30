package com.vulntrend.batch.global.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** NVD 설정. apiKey 값은 어떤 로그에도 남기지 않는다. 비어 있을 때 거부하는 검사는 NVD 호출 빈에서(Phase 1.1) */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "nvd")
public class NvdProperties {

    private final String apiKey;
}
