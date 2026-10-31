package com.vulntrend.batch.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사이트 데이터 게시 설정(WBS 4.3). repoDir = git 저장소 루트(기본 .., 배치는 batch/ 에서 돈다),
 * remote = push 할 원격 이름(기본 origin), commandTimeout = git 명령 하나의 시간 한도(기본 2분).
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "publish")
public class PublishProperties {

    private final String repoDir;
    private final String remote;
    private final Duration commandTimeout;
}
