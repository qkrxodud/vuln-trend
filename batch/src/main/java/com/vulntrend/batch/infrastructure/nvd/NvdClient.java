package com.vulntrend.batch.infrastructure.nvd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import com.vulntrend.batch.global.config.NvdProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import com.vulntrend.batch.infrastructure.nvd.dto.NvdPage;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * NVD CVE API 2.0 호출(WBS 1.1). 마지막 수정 시각 구간으로 바뀐 CVE 를 쪽 단위로 받아 넘긴다.
 * - 구간이 120일을 넘으면 나눠 부른다. 쪽은 startIndex 로 끝까지 따라간다.
 * - 매 요청 전에 requestInterval 만큼 쉰다(열쇠가 있을 때 30초에 50번). 상태를 두지 않으려고 "직전 요청 시각"을 기억하지 않는다.
 * - 429·5xx·연결 실패는 retryBackoff 부터 두 배씩 쉬며 maxAttempts 번까지 부른다. 그 밖의 4xx 는 바로 실패.
 * - 열쇠는 요청 머리(apiKey)에만 담는다. 실패 사유와 로그에는 주소·응답 본문·열쇠를 넣지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NvdClient {

    private static final DateTimeFormatter NVD_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx").withZone(ZoneOffset.UTC);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String SOURCE = SourceName.NVD.getKey();

    private final NvdProperties properties;
    private final HttpClient nvdHttpClient;
    private final ObjectMapper objectMapper;
    private final RequestPause pause;

    @PostConstruct
    void requireApiKey() {
        String key = properties.getApiKey();
        if (Objects.isNull(key) || key.isBlank()) {
            throw new IllegalStateException(
                    "NVD collection needs NVD_API_KEY. Set it in .env at the repository root before running the batch.");
        }
    }

    /** 구간 안에서 바뀐 CVE 를 쪽마다 pageHandler 에 넘기고, 받은 CVE 수를 돌려준다 */
    public int fetch(SyncWindow window, Consumer<NvdPage> pageHandler) {
        int received = 0;
        for (SyncWindow slice : window.splitBy(properties.getMaxWindow())) {
            received += fetchSlice(slice, pageHandler);
        }
        return received;
    }

    private int fetchSlice(SyncWindow slice, Consumer<NvdPage> pageHandler) {
        int startIndex = 0;
        int received = 0;
        while (true) {
            NvdPage page = requestPage(slice, startIndex);
            pageHandler.accept(page);
            received += page.vulnerabilities().size();
            startIndex = page.startIndex() + page.vulnerabilities().size();
            if (startIndex >= page.totalResults()) {
                log.info("NVD returned {} records for window {}.", received, slice);
                return received;
            }
            requireProgress(page, slice);
        }
    }

    private void requireProgress(NvdPage page, SyncWindow slice) {
        if (page.vulnerabilities().isEmpty()) {
            throw new SourceFetchException(SOURCE, "empty page at index " + page.startIndex() + " of "
                    + page.totalResults() + " for window " + slice + ".");
        }
    }

    private NvdPage requestPage(SyncWindow slice, int startIndex) {
        HttpRequest request = HttpRequest.newBuilder(pageUri(slice, startIndex))
                .header("apiKey", properties.getApiKey())
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        int attempts = properties.getMaxAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            pauseBefore(attempt);
            Optional<HttpResponse<String>> response = send(request, attempt, slice);
            if (response.isPresent() && response.get().statusCode() == 200) {
                return parse(response.get().body(), slice);
            }
            response.ifPresent(answer -> rejectUnlessRetryable(answer.statusCode(), slice));
        }
        throw new SourceFetchException(SOURCE, "no usable answer after " + attempts + " attempts for window " + slice + ".");
    }

    private URI pageUri(SyncWindow slice, int startIndex) {
        return URI.create(properties.getBaseUrl()
                + "?lastModStartDate=" + nvdTime(slice.getFrom())
                + "&lastModEndDate=" + nvdTime(slice.getUntil())
                + "&resultsPerPage=" + properties.getResultsPerPage()
                + "&startIndex=" + startIndex);
    }

    private static String nvdTime(Instant instant) {
        return URLEncoder.encode(NVD_TIME.format(instant), StandardCharsets.UTF_8);
    }

    // 첫 시도 전에는 호출 한도 간격, 다시 부를 때는 retryBackoff × 2^(시도-2)
    private void pauseBefore(int attempt) {
        Duration wait = attempt == 1
                ? properties.getRequestInterval()
                : properties.getRetryBackoff().multipliedBy(1L << (attempt - 2));
        try {
            pause.pause(wait);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException(SOURCE, "interrupted while waiting between requests.");
        }
    }

    private Optional<HttpResponse<String>> send(HttpRequest request, int attempt, SyncWindow slice) {
        try {
            return Optional.of(nvdHttpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (IOException failure) {
            log.warn("NVD request failed with {} (attempt {}/{}) for window {}.",
                    failure.getClass().getSimpleName(), attempt, properties.getMaxAttempts(), slice);
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException(SOURCE, "interrupted while waiting for an answer.");
        }
    }

    private void rejectUnlessRetryable(int status, SyncWindow slice) {
        if (status == 429 || status >= 500) {
            log.warn("NVD answered HTTP {} for window {}; retrying.", status, slice);
            return;
        }
        throw new SourceFetchException(SOURCE, "HTTP " + status + " for window " + slice + ".");
    }

    private NvdPage parse(String body, SyncWindow slice) {
        try {
            return objectMapper.readValue(body, NvdPage.class);
        } catch (JsonProcessingException failure) {
            throw new SourceFetchException(SOURCE, "unreadable answer (" + failure.getClass().getSimpleName()
                    + ") for window " + slice + ".");
        }
    }
}
