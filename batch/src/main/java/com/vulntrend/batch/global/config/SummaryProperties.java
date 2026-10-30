package com.vulntrend.batch.global.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 한국어 요약 설정. dailyLimit = 하루 요약 상한(기본 20) */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "summary")
public class SummaryProperties {

    private final int dailyLimit;
}
