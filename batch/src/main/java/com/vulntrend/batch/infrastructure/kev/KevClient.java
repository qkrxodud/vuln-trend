package com.vulntrend.batch.infrastructure.kev;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.global.config.KevProperties;
import com.vulntrend.batch.infrastructure.feed.FeedDownloader;
import com.vulntrend.batch.infrastructure.nvd.RequestPause;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** CISA KEV JSON 파일 하나를 내려받는다(WBS 2.1). 구간이 없는 전체 목록이다. 재시도 규칙은 FeedDownloader 에 있다 */
@Component
@RequiredArgsConstructor
public class KevClient {

    private final KevProperties properties;
    private final RequestPause pause;

    public String fetch() {
        byte[] body = FeedDownloader.of(SourceName.KEV.getKey(), properties.getUrl(), properties.getMaxAttempts(),
                properties.getRetryBackoff(), properties.getRequestTimeout(), pause).download();
        return new String(body, StandardCharsets.UTF_8);
    }
}
