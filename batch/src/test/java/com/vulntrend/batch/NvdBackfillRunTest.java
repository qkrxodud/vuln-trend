package com.vulntrend.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.application.collect.CollectService;
import com.vulntrend.batch.application.product.ProductAssignService;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.product.ProductAliases;
import com.vulntrend.batch.fake.FakeProductAliasCatalog;
import com.vulntrend.batch.fake.FakeProductAssignmentRepository;
import com.vulntrend.batch.global.config.NvdProperties;
import com.vulntrend.batch.infrastructure.nvd.NvdClient;
import com.vulntrend.batch.infrastructure.nvd.NvdCollector;
import com.vulntrend.batch.infrastructure.nvd.NvdCveMapper;
import com.vulntrend.batch.infrastructure.nvd.NvdSamples;
import com.vulntrend.batch.infrastructure.nvd.NvdStubServer;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import com.vulntrend.batch.infrastructure.persistence.syncstate.JdbcSyncStateRepository;
import com.vulntrend.batch.infrastructure.persistence.vulnerability.JdbcVulnerabilityRepository;
import com.vulntrend.batch.runner.BatchRunner;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** WBS 1.5: 지난 1년 한 번 가져오기를 흉내 NVD 와 실제 저장 구조로 돌린다. 실제 1년치는 NVD 열쇠를 넣은 뒤 사용자가 돌린다 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcVulnerabilityRepository.class, JdbcSyncStateRepository.class})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("지난 1년치 한 번 가져오기 — 실제 저장 구조에서")
class NvdBackfillRunTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Autowired
    private JdbcVulnerabilityRepository vulnerabilities;

    @Autowired
    private JdbcSyncStateRepository syncStates;

    @Autowired
    private JdbcTemplate jdbc;

    private NvdStubServer nvd;

    @BeforeEach
    void startNvd() throws IOException {
        nvd = new NvdStubServer();
    }

    @AfterEach
    void stopNvd() {
        nvd.close();
    }

    @Test
    @DisplayName("365일을 120일씩 네 구간으로 나눠 빈틈 없이 가져온다")
    void splitsYearIntoFourWindows() {
        nvd.pageOf(List.of(NvdSamples.log4shell())).pageOf(List.of(NvdSamples.CHROME))
                .pageOf(List.of(NvdSamples.WORDPRESS)).pageOf(List.of(NvdSamples.REJECTED));

        runner().run("--backfill-days=365");

        assertThat(nvd.received()).extracting(r -> r.param("lastModStartDate")).containsExactly(
                "2025-10-31T00:00:00.000+00:00", "2026-02-28T00:00:00.000+00:00",
                "2026-06-28T00:00:00.000+00:00", "2026-10-26T00:00:00.000+00:00");
        assertThat(nvd.received().get(3).param("lastModEndDate")).isEqualTo("2026-10-31T00:00:00.000+00:00");
    }

    @Test
    @DisplayName("다 가져오면 취약점이 적재되고 다음 하루 한 번 실행은 지금부터 이어 간다")
    void loadsAndAdvances() {
        nvd.pageOf(List.of(NvdSamples.log4shell(), NvdSamples.CHROME)).pageOf(List.of(NvdSamples.WORDPRESS))
                .pageOf(List.of()).pageOf(List.of(NvdSamples.REJECTED));

        runner().run("--backfill-days=365");

        assertThat(jdbc.queryForObject("select count(*) from vulnerability", Integer.class)).isEqualTo(3);
        assertThat(syncStates.findBySource(SourceName.NVD).getLastSyncedAt()).contains(NOW);
    }

    @Test
    @DisplayName("실행 기록에 적재 수·철회되어 빠진 수·심각도 미평가 비율이 남는다")
    void logsTally(CapturedOutput output) {
        nvd.pageOf(List.of(NvdSamples.log4shell(), NvdSamples.CHROME, NvdSamples.WORDPRESS, NvdSamples.REJECTED))
                .pageOf(List.of()).pageOf(List.of()).pageOf(List.of());

        runner().run("--backfill-days=365");

        assertThat(output).contains("NVD loaded 3 records");
        assertThat(output).contains("1 rejected (0 marked, 1 skipped as new)");
        assertThat(output).contains("1 unrated (33%)");
        assertThat(output).contains("Backfill of NVD for the last 365 days loaded 3 records");
    }

    private BatchRunner runner() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        NvdProperties properties = new NvdProperties("nvd-key", nvd.baseUrl(), 2000, Duration.ofDays(120),
                Duration.ZERO, 2, Duration.ZERO);
        NvdClient client = new NvdClient(properties, HttpClient.newHttpClient(), new ObjectMapper(), duration -> { });
        NvdCollector collector = new NvdCollector(client, new NvdCveMapper(new ObjectMapper()), vulnerabilities, clock);
        ProductAssignService assignment = new ProductAssignService(
                new FakeProductAliasCatalog(ProductAliases.of(List.of())),
                new FakeProductAssignmentRepository(List.of(), List.of()));
        return new BatchRunner(new CollectService(List.of(collector), syncStates, clock), assignment);
    }
}
