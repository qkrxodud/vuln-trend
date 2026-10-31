package com.vulntrend.batch.infrastructure.kev;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.global.config.KevProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import com.vulntrend.batch.infrastructure.nvd.RequestPause;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CISA KEV JSON 파일 하나를 내려받는다(WBS 2.1). 구간이 없는 전체 목록이다.
 * - 5xx·429·연결 실패는 retryBackoff 부터 두 배씩 쉬며 maxAttempts 번까지 부른다. 그 밖의 4xx 는 바로 실패.
 * - HttpClient 는 빈으로 두지 않고 여기서 만든다. HttpClient 빈이 둘이 되면 NVD 쪽 타입 주입이 모호해진다.
 * - 실패 사유와 로그에는 응답 본문을 넣지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KevClient {

    private static final String SOURCE = SourceName.KEV.getKey();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final KevProperties properties;
    private final RequestPause pause;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String fetch() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getUrl()))
                .timeout(properties.getRequestTimeout())
                .GET()
                .build();
        int attempts = properties.getMaxAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            pauseBefore(attempt);
            Optional<HttpResponse<String>> response = send(request, attempt);
            if (response.isPresent() && response.get().statusCode() == 200) {
                return response.get().body();
            }
            response.ifPresent(answer -> rejectUnlessRetryable(answer.statusCode()));
        }
        throw new SourceFetchException(SOURCE, "no usable answer after " + attempts + " attempts.");
    }

    // 첫 시도는 바로, 다시 부를 때는 retryBackoff × 2^(시도-2)
    private void pauseBefore(int attempt) {
        if (attempt == 1) {
            return;
        }
        try {
            pause.pause(properties.getRetryBackoff().multipliedBy(1L << (attempt - 2)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException(SOURCE, "interrupted while waiting between requests.");
        }
    }

    private Optional<HttpResponse<String>> send(HttpRequest request, int attempt) {
        try {
            return Optional.of(http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (IOException failure) {
            log.warn("KEV request failed with {} (attempt {}/{}).",
                    failure.getClass().getSimpleName(), attempt, properties.getMaxAttempts());
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException(SOURCE, "interrupted while waiting for an answer.");
        }
    }

    private void rejectUnlessRetryable(int status) {
        if (status == 429 || status >= 500) {
            log.warn("KEV answered HTTP {}; retrying.", status);
            return;
        }
        throw new SourceFetchException(SOURCE, "HTTP " + status + ".");
    }
}
