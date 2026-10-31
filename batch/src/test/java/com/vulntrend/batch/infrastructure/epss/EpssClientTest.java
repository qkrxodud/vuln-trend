package com.vulntrend.batch.infrastructure.epss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.global.config.EpssProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import com.vulntrend.batch.infrastructure.feed.FeedStubServer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EPSS 점수 파일 내려받기")
class EpssClientTest {

    private final FeedStubServer first = new FeedStubServer();
    private final List<Duration> pauses = new ArrayList<>();
    private final EpssClient client = new EpssClient(
            new EpssProperties(first.url(), 2, Duration.ofSeconds(2), Duration.ofSeconds(5)), pauses::add);

    @AfterEach
    void stop() {
        first.close();
    }

    @Test
    @DisplayName("압축된 점수 파일을 풀어 한 줄씩 넘긴다")
    void unzipsLineByLine() {
        first.answer(200, EpssFiles.gzip("#model_version:v2026.06.15", "cve,epss,percentile",
                "CVE-2021-44228,0.99999,1.0"));
        List<String> lines = new ArrayList<>();

        client.fetch(lines::add);

        assertThat(lines).containsExactly("#model_version:v2026.06.15", "cve,epss,percentile",
                "CVE-2021-44228,0.99999,1.0");
    }

    @Test
    @DisplayName("FIRST 서버가 잠시 답하지 못하면 쉬었다가 다시 부른다")
    void retriesServerTrouble() {
        first.answer(502, "busy").answer(200, EpssFiles.gzip("cve,epss,percentile"));
        List<String> lines = new ArrayList<>();

        client.fetch(lines::add);

        assertThat(lines).containsExactly("cve,epss,percentile");
        assertThat(pauses).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("압축 파일이 아니면 EPSS 수집이 실패한다")
    void notGzipFails() {
        first.answer(200, "cve,epss,percentile");

        assertThatThrownBy(() -> client.fetch(line -> { }))
                .isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("epss");
    }

    @Test
    @DisplayName("압축 파일이 중간에 끊겨 있으면 EPSS 수집이 실패한다")
    void truncatedGzipFails() {
        byte[] whole = EpssFiles.gzip("cve,epss,percentile", "CVE-2021-44228,0.99999,1.0");
        byte[] cut = java.util.Arrays.copyOf(whole, whole.length - 10);
        first.answer(200, cut);

        assertThatThrownBy(() -> client.fetch(line -> { })).isInstanceOf(SourceFetchException.class);
    }

    @Test
    @DisplayName("주소가 없다는 답이면 다시 부르지 않고 바로 실패한다")
    void notFoundFailsAtOnce() {
        first.answer(404, "gone");

        assertThatThrownBy(() -> client.fetch(line -> { })).isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("404");
        assertThat(first.requests()).isEqualTo(1);
    }
}
