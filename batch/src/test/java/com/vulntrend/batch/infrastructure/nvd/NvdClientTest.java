package com.vulntrend.batch.infrastructure.nvd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import com.vulntrend.batch.global.config.NvdProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import com.vulntrend.batch.infrastructure.nvd.dto.NvdPage;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("NVD 호출")
@ExtendWith(OutputCaptureExtension.class)
class NvdClientTest {

    private static final String KEY = "nvd-key-7f3a9c";
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Duration INTERVAL = Duration.ofMillis(600);
    private static final Duration BACKOFF = Duration.ofSeconds(2);

    private NvdStubServer nvd;
    private final List<Duration> pauses = new ArrayList<>();
    private final List<NvdPage> pages = new ArrayList<>();

    @BeforeEach
    void startNvd() throws IOException {
        nvd = new NvdStubServer();
    }

    @AfterEach
    void stopNvd() {
        nvd.close();
    }

    @Nested
    @DisplayName("한 구간을 부를 때")
    class OneWindow {

        @Test
        @DisplayName("마지막 수정 시각의 시작·끝과 한 쪽 크기를 NVD 형식으로 붙여 부른다")
        void sendsWindowAndPageSize() {
            nvd.page(0, 1, 1);

            client(nvd.baseUrl(), 5).fetch(SyncWindow.of(FROM, FROM.plus(Duration.ofDays(1))), pages::add);

            NvdStubServer.Received request = nvd.received().get(0);
            assertThat(request.param("lastModStartDate")).isEqualTo("2026-10-01T00:00:00.000+00:00");
            assertThat(request.param("lastModEndDate")).isEqualTo("2026-10-02T00:00:00.000+00:00");
            assertThat(request.param("resultsPerPage")).isEqualTo("2000");
            assertThat(request.param("startIndex")).isEqualTo("0");
        }

        @Test
        @DisplayName("NVD 열쇠는 주소가 아니라 요청 머리에 담아 보낸다")
        void sendsKeyInHeader() {
            nvd.page(0, 1, 1);

            client(nvd.baseUrl(), 5).fetch(oneDay(), pages::add);

            assertThat(nvd.received().get(0).apiKey()).isEqualTo(KEY);
            assertThat(nvd.received().get(0).param("apiKey")).isNull();
        }

        @Test
        @DisplayName("결과가 여러 쪽이면 마지막 쪽까지 이어서 가져온다")
        void followsPages() {
            // given: 전체 3건, 한 쪽에 2건
            nvd.page(0, 2, 3).page(2, 1, 3);

            // when
            int received = client(nvd.baseUrl(), 5).fetch(oneDay(), pages::add);

            // then
            assertThat(received).isEqualTo(3);
            assertThat(pages).hasSize(2);
            assertThat(nvd.received()).extracting(r -> r.param("startIndex")).containsExactly("0", "2");
            assertThat(pages.get(1).vulnerabilities().get(0).cve().get("id").asText()).isEqualTo("CVE-2026-10002");
        }

        @Test
        @DisplayName("바뀐 CVE 가 없는 구간은 한 번만 부르고 끝난다")
        void emptyWindow() {
            nvd.page(0, 0, 0);

            int received = client(nvd.baseUrl(), 5).fetch(oneDay(), pages::add);

            assertThat(received).isZero();
            assertThat(nvd.received()).hasSize(1);
        }

        @Test
        @DisplayName("끝나기 전에 빈 쪽이 오면 더 부르지 않고 실패로 알린다")
        void emptyPageBeforeEnd() {
            nvd.page(0, 0, 5);

            assertThatThrownBy(() -> client(nvd.baseUrl(), 5).fetch(oneDay(), pages::add))
                    .isInstanceOf(SourceFetchException.class);
            assertThat(nvd.received()).hasSize(1);
        }
    }

    @Test
    @DisplayName("수집 구간이 120일을 넘으면 120일씩 나눠 부른다")
    void splitsLongWindow() {
        // given: 300일 구간 → 120일 + 120일 + 60일
        nvd.page(0, 0, 0).page(0, 0, 0).page(0, 0, 0);

        client(nvd.baseUrl(), 5).fetch(SyncWindow.of(FROM, FROM.plus(Duration.ofDays(300))), pages::add);

        assertThat(nvd.received()).extracting(r -> r.param("lastModStartDate")).containsExactly(
                "2026-10-01T00:00:00.000+00:00", "2027-01-29T00:00:00.000+00:00", "2027-05-29T00:00:00.000+00:00");
        assertThat(nvd.received().get(2).param("lastModEndDate")).isEqualTo("2027-07-28T00:00:00.000+00:00");
    }

    @Nested
    @DisplayName("호출 한도와 다시 부르기")
    class Pacing {

        @Test
        @DisplayName("매번 부르기 전에 호출 한도(30초에 50번)에 맞춰 쉰다")
        void pausesBeforeEachRequest() {
            nvd.page(0, 2, 3).page(2, 1, 3);

            client(nvd.baseUrl(), 5).fetch(oneDay(), pages::add);

            assertThat(pauses).containsExactly(INTERVAL, INTERVAL);
        }

        @Test
        @DisplayName("너무 자주 불렀다는 응답(429)을 받으면 쉬었다가 다시 부른다")
        void retriesAfterTooManyRequests() {
            nvd.answer(429, "slow down").page(0, 1, 1);

            int received = client(nvd.baseUrl(), 5).fetch(oneDay(), pages::add);

            assertThat(received).isEqualTo(1);
            assertThat(nvd.received()).hasSize(2);
            assertThat(pauses).containsExactly(INTERVAL, BACKOFF);
        }

        @Test
        @DisplayName("NVD 쪽 장애(5xx)가 이어지면 쉬는 시간을 두 배씩 늘리며 정해진 횟수만큼만 부르고 실패로 알린다")
        void givesUpAfterMaxAttempts() {
            nvd.answer(503, "down").answer(502, "down").answer(500, "down");

            assertThatThrownBy(() -> client(nvd.baseUrl(), 3).fetch(oneDay(), pages::add))
                    .isInstanceOf(SourceFetchException.class)
                    .hasMessageContaining("3 attempts");
            assertThat(nvd.received()).hasSize(3);
            assertThat(pauses).containsExactly(INTERVAL, BACKOFF, BACKOFF.multipliedBy(2));
        }

        @Test
        @DisplayName("잘못된 요청이라는 응답(4xx)은 다시 부르지 않고 바로 실패로 알린다")
        void doesNotRetryClientError() {
            nvd.answer(404, "not found");

            assertThatThrownBy(() -> client(nvd.baseUrl(), 5).fetch(oneDay(), pages::add))
                    .isInstanceOf(SourceFetchException.class)
                    .hasMessageContaining("HTTP 404");
            assertThat(nvd.received()).hasSize(1);
        }

        @Test
        @DisplayName("NVD 에 닿지 않으면 정해진 횟수만큼 다시 부른 뒤 실패로 알린다")
        void retriesWhenUnreachable() throws IOException {
            String closed = "http://127.0.0.1:" + freePort() + "/cves";

            assertThatThrownBy(() -> client(closed, 2).fetch(oneDay(), pages::add))
                    .isInstanceOf(SourceFetchException.class)
                    .hasMessageContaining("2 attempts");
            assertThat(pauses).hasSize(2);
        }

        @Test
        @DisplayName("쉬는 동안 실행이 중단되면 더 부르지 않고 실패로 알린다")
        void stopsWhenInterrupted() {
            NvdClient client = new NvdClient(properties(nvd.baseUrl(), 5), HttpClient.newHttpClient(),
                    new ObjectMapper(), duration -> {
                        throw new InterruptedException("stop");
                    });

            assertThatThrownBy(() -> client.fetch(oneDay(), pages::add)).isInstanceOf(SourceFetchException.class);
            assertThat(Thread.interrupted()).isTrue();
            assertThat(nvd.received()).isEmpty();
        }
    }

    @Test
    @DisplayName("읽을 수 없는 답이 오면 실패로 알린다")
    void unreadableAnswer() {
        nvd.answer(200, "<html>maintenance</html>");

        assertThatThrownBy(() -> client(nvd.baseUrl(), 5).fetch(oneDay(), pages::add))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("unreadable");
    }

    @Test
    @DisplayName("NVD 열쇠 값은 실행 기록과 실패 사유 어디에도 남기지 않는다")
    void keyNeverLeaks(CapturedOutput output) throws IOException {
        // given: 닿지 않는 주소 + 장애 응답 — 실패 기록이 남는 길을 모두 지난다
        nvd.answer(503, "down " + KEY).answer(404, "bad key " + KEY);
        String closed = "http://127.0.0.1:" + freePort() + "/cves";

        // when
        Throwable serverFailure = catchFailure(() -> client(nvd.baseUrl(), 5).fetch(oneDay(), pages::add));
        Throwable unreachable = catchFailure(() -> client(closed, 1).fetch(oneDay(), pages::add));

        // then
        assertThat(serverFailure.getMessage()).doesNotContain(KEY);
        assertThat(unreachable.getMessage()).doesNotContain(KEY);
        assertThat(output).doesNotContain(KEY);
        assertThat(output).contains("NVD");
    }

    private NvdClient client(String baseUrl, int maxAttempts) {
        return new NvdClient(properties(baseUrl, maxAttempts), HttpClient.newHttpClient(), new ObjectMapper(),
                pauses::add);
    }

    private static NvdProperties properties(String baseUrl, int maxAttempts) {
        return new NvdProperties(KEY, baseUrl, 2000, Duration.ofDays(120), INTERVAL, maxAttempts, BACKOFF);
    }

    private static SyncWindow oneDay() {
        return SyncWindow.of(FROM, FROM.plus(Duration.ofDays(1)));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Throwable catchFailure(Runnable call) {
        try {
            call.run();
        } catch (SourceFetchException failure) {
            return failure;
        }
        throw new AssertionError("expected a source failure");
    }
}
