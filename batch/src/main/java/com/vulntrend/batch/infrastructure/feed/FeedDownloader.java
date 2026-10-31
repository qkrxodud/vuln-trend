package com.vulntrend.batch.infrastructure.feed;

import com.vulntrend.batch.global.error.exception.SourceFetchException;
import com.vulntrend.batch.infrastructure.nvd.RequestPause;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * 파일 하나짜리 외부 소스(CISA KEV JSON, FIRST EPSS gzip CSV)를 통째로 내려받는다. 부를 때마다 새로 만든다(빈 아님).
 * - 리다이렉트를 따라간다(EPSS current 주소는 그날 파일로 302).
 * - 5xx·429·연결 실패는 retryBackoff 부터 두 배씩 쉬며 maxAttempts 번까지 부른다. 그 밖의 4xx 는 바로 실패.
 * - HttpClient 는 빈으로 두지 않는다. HttpClient 빈이 둘이 되면 NVD 쪽 타입 주입이 모호해진다(백엔드 A 와 합의).
 * - 실패 사유와 로그에는 응답 본문을 넣지 않는다.
 */
@Slf4j
public class FeedDownloader {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String sourceKey;
    private final URI uri;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final Duration requestTimeout;
    private final RequestPause pause;

    @Builder(access = AccessLevel.PRIVATE)
    private FeedDownloader(String sourceKey, URI uri, int maxAttempts, Duration retryBackoff, Duration requestTimeout,
            RequestPause pause) {
        this.sourceKey = sourceKey;
        this.uri = uri;
        this.maxAttempts = maxAttempts;
        this.retryBackoff = retryBackoff;
        this.requestTimeout = requestTimeout;
        this.pause = pause;
    }

    public static FeedDownloader of(String sourceKey, String url, int maxAttempts, Duration retryBackoff,
            Duration requestTimeout, RequestPause pause) {
        return FeedDownloader.builder()
                .sourceKey(sourceKey)
                .uri(URI.create(url))
                .maxAttempts(maxAttempts)
                .retryBackoff(retryBackoff)
                .requestTimeout(requestTimeout)
                .pause(pause)
                .build();
    }

    public byte[] download() {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(requestTimeout).GET().build();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            pauseBefore(attempt);
            Optional<HttpResponse<byte[]>> response = send(request, attempt);
            if (response.isPresent() && response.get().statusCode() == 200) {
                return response.get().body();
            }
            response.ifPresent(answer -> rejectUnlessRetryable(answer.statusCode()));
        }
        throw new SourceFetchException(sourceKey, "no usable answer after " + maxAttempts + " attempts.");
    }

    // 첫 시도는 바로, 다시 부를 때는 retryBackoff × 2^(시도-2)
    private void pauseBefore(int attempt) {
        if (attempt == 1) {
            return;
        }
        try {
            pause.pause(retryBackoff.multipliedBy(1L << (attempt - 2)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException(sourceKey, "interrupted while waiting between requests.");
        }
    }

    private Optional<HttpResponse<byte[]>> send(HttpRequest request, int attempt) {
        try {
            return Optional.of(HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray()));
        } catch (IOException failure) {
            log.warn("Source {} request failed with {} (attempt {}/{}).",
                    sourceKey, failure.getClass().getSimpleName(), attempt, maxAttempts);
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException(sourceKey, "interrupted while waiting for an answer.");
        }
    }

    private void rejectUnlessRetryable(int status) {
        if (status == 429 || status >= 500) {
            log.warn("Source {} answered HTTP {}; retrying.", sourceKey, status);
            return;
        }
        throw new SourceFetchException(sourceKey, "HTTP " + status + ".");
    }
}
