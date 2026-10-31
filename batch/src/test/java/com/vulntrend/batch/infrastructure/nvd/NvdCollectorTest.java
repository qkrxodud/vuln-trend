package com.vulntrend.batch.infrastructure.nvd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.CvssSelection;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import com.vulntrend.batch.domain.vulnerability.Vulnerability;
import com.vulntrend.batch.fake.FakeVulnerabilityRepository;
import com.vulntrend.batch.global.config.NvdProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("NVD 수집")
@ExtendWith(OutputCaptureExtension.class)
class NvdCollectorTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final SyncWindow LAST_DAY = SyncWindow.of(NOW.minus(Duration.ofDays(1)), NOW);
    private static final CveId REJECTED_ID = CveId.from("CVE-2026-30003");

    private NvdStubServer nvd;
    private final FakeVulnerabilityRepository vulnerabilities = new FakeVulnerabilityRepository();

    @BeforeEach
    void startNvd() throws IOException {
        nvd = new NvdStubServer();
    }

    @AfterEach
    void stopNvd() {
        nvd.close();
    }

    @Test
    @DisplayName("NVD 소스의 수집기다")
    void sourceIsNvd() {
        assertThat(collector().source()).isEqualTo(SourceName.NVD);
    }

    @Test
    @DisplayName("살아 있는 CVE 는 목록에 올리고 적재한 수를 알려 준다")
    void loadsAccepted() {
        nvd.pageOf(List.of(NvdSamples.CHROME, NvdSamples.WORDPRESS, NvdSamples.REJECTED));

        CollectResult result = collector().collect(LAST_DAY);

        assertThat(result.getSource()).isEqualTo(SourceName.NVD);
        assertThat(result.getLoadedCount()).isEqualTo(2);
        assertThat(vulnerabilities.vulnerabilities()).containsOnlyKeys(
                CveId.from("CVE-2026-30001"), CveId.from("CVE-2026-30002"));
    }

    @Test
    @DisplayName("NVD 가 철회한 CVE 는 새로 올리지 않고 이미 있던 CVE 면 철회 표시만 붙인다")
    void rejectedOnlyMarksExisting() {
        // given: 철회된 CVE 가 이미 목록에 있었다
        vulnerabilities.with(Vulnerability.fromNvd(REJECTED_ID, "x.", CvssSelection.unrated(), NOW, NOW,
                List.of(), List.of()));
        nvd.pageOf(List.of(NvdSamples.REJECTED));

        // when
        collector().collect(LAST_DAY);

        // then
        assertThat(vulnerabilities.rejected()).containsExactly(REJECTED_ID);
    }

    @Test
    @DisplayName("받은 CVE 는 철회된 것까지 모두 원본 그대로 이번 수집 시각과 함께 보관한다")
    void keepsEveryRaw() {
        nvd.pageOf(List.of(NvdSamples.CHROME, NvdSamples.REJECTED));

        collector().collect(LAST_DAY);

        assertThat(vulnerabilities.raws()).hasSize(2);
        assertThat(vulnerabilities.raws()).allSatisfy(raw -> {
            assertThat(raw.getSource()).isEqualTo(SourceName.NVD);
            assertThat(raw.getFetchedAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("여러 쪽으로 온 결과도 쪽마다 목록에 올린다")
    void storesEveryPage() {
        nvd.page(0, 2, 3).page(2, 1, 3);

        CollectResult result = collector().collect(LAST_DAY);

        assertThat(result.getLoadedCount()).isEqualTo(3);
        assertThat(vulnerabilities.vulnerabilities()).hasSize(3);
    }

    @Test
    @DisplayName("실행 기록에 적재 수·철회 수·심각도 미평가 비율을 남긴다")
    void logsTally(CapturedOutput output) {
        nvd.pageOf(List.of(NvdSamples.CHROME, NvdSamples.WORDPRESS, NvdSamples.REJECTED));

        collector().collect(LAST_DAY);

        assertThat(output).contains("NVD loaded 2 records");
        assertThat(output).contains("1 rejected (0 marked, 1 skipped as new)");
        assertThat(output).contains("1 unrated (50%)");
    }

    @Test
    @DisplayName("NVD 가 실패하면 수집이 멈추고 실패로 알린다")
    void failurePropagates() {
        nvd.answer(404, "not found");

        assertThatThrownBy(() -> collector().collect(LAST_DAY)).isInstanceOf(SourceFetchException.class);
    }

    private NvdCollector collector() {
        NvdProperties properties = new NvdProperties("nvd-key", nvd.baseUrl(), 2000, Duration.ofDays(120),
                Duration.ZERO, 2, Duration.ZERO);
        NvdClient client = new NvdClient(properties, HttpClient.newHttpClient(), new ObjectMapper(), duration -> { });
        return new NvdCollector(client, new NvdCveMapper(new ObjectMapper()), vulnerabilities,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
