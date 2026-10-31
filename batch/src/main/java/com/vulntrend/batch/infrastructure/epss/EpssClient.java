package com.vulntrend.batch.infrastructure.epss;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.global.config.EpssProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import com.vulntrend.batch.infrastructure.feed.FeedDownloader;
import com.vulntrend.batch.infrastructure.nvd.RequestPause;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FIRST EPSS 전체 점수 파일(gzip CSV, 2026-10-31 기준 2.8MB·약 40만 줄)을 내려받아 풀면서 한 줄씩 넘긴다(WBS 2.2).
 * 통째로 받은 뒤 푼다 — 재시도가 내려받기 전체를 감싸고, 끊긴 파일은 풀 때 드러난다. 재시도 규칙은 FeedDownloader 에 있다.
 */
@Component
@RequiredArgsConstructor
public class EpssClient {

    private static final String SOURCE = SourceName.EPSS.getKey();

    private final EpssProperties properties;
    private final RequestPause pause;

    public void fetch(Consumer<String> lineHandler) {
        byte[] gzip = FeedDownloader.of(SOURCE, properties.getUrl(), properties.getMaxAttempts(),
                properties.getRetryBackoff(), properties.getRequestTimeout(), pause).download();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ByteArrayInputStream(gzip)), StandardCharsets.UTF_8))) {
            reader.lines().forEach(lineHandler);
        } catch (IOException | UncheckedIOException unreadable) {
            throw new SourceFetchException(SOURCE, "the score file is not a complete gzip file ("
                    + unreadable.getClass().getSimpleName() + ").");
        }
    }
}
