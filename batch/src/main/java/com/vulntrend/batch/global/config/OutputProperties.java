package com.vulntrend.batch.global.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 사이트 데이터 파일을 쓰는 곳(output.dir, 기본 ../web/public/data) */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "output")
public class OutputProperties {

    private final String dir;
}
