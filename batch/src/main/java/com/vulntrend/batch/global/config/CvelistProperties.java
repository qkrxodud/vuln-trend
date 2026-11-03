package com.vulntrend.batch.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CVE.org cvelistV5 보강 설정(S3a). baseUrl 뒤에 /CVE-번호 를 붙여 부른다 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "cvelist")
public class CvelistProperties {

    private final String baseUrl;
    // 대표 제품 없는 CVE 를 찾을 최근 기간(발행 시각 기준)
    private final Duration lookback;
    // 한 실행에서 부를 최대 건수 — 30일 백필을 여러 실행에 나눈다
    private final int maxPerRun;
    // 못 채운 CVE 를 다시 부르기까지 기다리는 간격
    private final Duration retryAfter;
    // 이만큼 연달아 실패하면 이번 실행의 보강을 멈춘다(CVE.org 장애)
    private final int maxConsecutiveFailures;
    private final Duration requestInterval;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final Duration requestTimeout;
}
