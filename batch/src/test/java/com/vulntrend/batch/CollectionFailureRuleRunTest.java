package com.vulntrend.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.application.collect.CollectService;
import com.vulntrend.batch.application.product.ProductAssignService;
import com.vulntrend.batch.application.score.ScoreService;
import com.vulntrend.batch.application.stat.DailyStatService;
import com.vulntrend.batch.application.summary.SummaryService;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.product.ProductAliases;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStatus;
import com.vulntrend.batch.fake.FakeProductAliasCatalog;
import com.vulntrend.batch.fake.FakeProductAssignmentRepository;
import com.vulntrend.batch.fake.FakePublisher;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import com.vulntrend.batch.fake.FakeSummaryRepository;
import com.vulntrend.batch.global.config.KevProperties;
import com.vulntrend.batch.global.config.NvdProperties;
import com.vulntrend.batch.global.config.SummaryProperties;
import com.vulntrend.batch.infrastructure.kev.KevCatalogParser;
import com.vulntrend.batch.infrastructure.kev.KevClient;
import com.vulntrend.batch.infrastructure.kev.KevCollector;
import com.vulntrend.batch.infrastructure.nvd.NvdClient;
import com.vulntrend.batch.infrastructure.nvd.NvdCollector;
import com.vulntrend.batch.infrastructure.nvd.NvdCveMapper;
import com.vulntrend.batch.infrastructure.nvd.NvdSamples;
import com.vulntrend.batch.infrastructure.nvd.NvdStubServer;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import com.vulntrend.batch.infrastructure.persistence.enrichment.JdbcKevRepository;
import com.vulntrend.batch.infrastructure.persistence.score.JdbcTrendScoreRepository;
import com.vulntrend.batch.infrastructure.persistence.stat.JdbcDailyStatRepository;
import com.vulntrend.batch.infrastructure.persistence.syncstate.JdbcSyncStateRepository;
import com.vulntrend.batch.infrastructure.persistence.vulnerability.JdbcVulnerabilityRepository;
import com.vulntrend.batch.runner.BatchRunner;
import com.vulntrend.batch.support.GateChecks;
import com.vulntrend.batch.support.SiteExports;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * WBS 2.4 DoD: 하루 한 번 실행에서 KEV 주소가 틀리면 점수 단계 앞에서 멈추고, 소스별 진행 기록은 따로 남는다.
 * NVD 는 흉내 서버, KEV 는 백엔드 B 의 실제 수집기(틀린 주소 또는 흉내 서버), 저장은 실제 저장 구조.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcVulnerabilityRepository.class, JdbcSyncStateRepository.class,
        JdbcKevRepository.class, JdbcTrendScoreRepository.class, JdbcDailyStatRepository.class})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("수집 실패 규칙 — 실제 저장 구조에서")
class CollectionFailureRuleRunTest {

    private static final Instant YESTERDAY = Instant.parse("2026-10-30T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Autowired
    private JdbcVulnerabilityRepository vulnerabilities;

    @Autowired
    private JdbcSyncStateRepository syncStates;

    @Autowired
    private JdbcKevRepository kevRepository;

    @Autowired
    private JdbcTrendScoreRepository trendScores;

    @Autowired
    private JdbcDailyStatRepository dailyStats;

    @Autowired
    private JdbcTemplate jdbc;

    private NvdStubServer nvd;
    private NvdStubServer kev;

    @BeforeEach
    void start() throws IOException {
        nvd = new NvdStubServer();
        kev = new NvdStubServer();
        nvd.pageOf(List.of(NvdSamples.log4shell(), NvdSamples.CHROME));
        // given: 어제 끝까지 NVD·KEV 를 모두 가져왔다
        syncStates.save(SyncState.restore(SourceName.NVD, YESTERDAY, YESTERDAY, SyncStatus.SUCCESS));
        syncStates.save(SyncState.restore(SourceName.KEV, YESTERDAY, YESTERDAY, SyncStatus.SUCCESS));
    }

    @AfterEach
    void stop() {
        nvd.close();
        kev.close();
    }

    @Nested
    @DisplayName("KEV 주소가 틀리면")
    class WrongKevUrl {

        @Test
        @DisplayName("대표 제품 고르기와 점수 매기기로 넘어가지 않는다")
        void stopsBeforeScoring(CapturedOutput output) throws IOException {
            catchThrowable(runner(closedUrl())::run);

            assertThat(output).contains("scoring and publishing are skipped");
            assertThat(output).contains("KEV");
            assertThat(output).doesNotContain("Primary product changed");
            assertThat(output).doesNotContain("Trend score recalculated");
            assertThat(output).doesNotContain("Daily statistics recalculated");
        }

        @Test
        @DisplayName("KEV 는 실패로 기록되고 마지막으로 가져온 시각이 그대로 남아 다음 실행에서 다시 가져온다")
        void kevFailsAndKeepsCursor() throws IOException {
            catchThrowable(runner(closedUrl())::run);

            SyncState state = syncStates.findBySource(SourceName.KEV);
            assertThat(state.getStatus()).isEqualTo(SyncStatus.FAIL);
            assertThat(state.getLastSyncedAt()).contains(YESTERDAY);
            assertThat(state.getLastRunAt()).contains(NOW);
        }

        @Test
        @DisplayName("NVD 는 KEV 실패와 상관없이 다 가져온 것으로 기록되고 진행 기록이 지금으로 옮겨진다")
        void nvdSucceedsIndependently() throws IOException {
            catchThrowable(runner(closedUrl())::run);

            SyncState state = syncStates.findBySource(SourceName.NVD);
            assertThat(state.getStatus()).isEqualTo(SyncStatus.SUCCESS);
            assertThat(state.getLastSyncedAt()).contains(NOW);
            assertThat(jdbc.queryForObject("select count(*) from vulnerability", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("KEV 까지 다 가져오면 같은 실행에서 NVD 가 새로 올린 CVE 에도 KEV 표시가 붙고 대표 제품·점수·일별 통계로 이어진다")
    void kevSucceeds(CapturedOutput output) throws IOException {
        kev.answer(200, kevCatalog());

        runner(kev.baseUrl()).run();

        assertThat(syncStates.findBySource(SourceName.KEV).getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(jdbc.queryForObject("select is_kev from vulnerability where vuln_id = 'CVE-2021-44228'",
                Boolean.class)).isTrue();
        assertThat(output).contains("Primary product changed");
        assertThat(output).contains("Trend score recalculated for 2 vulnerabilities");
        assertThat(output).contains("Daily statistics recalculated");
        assertThat(output).doesNotContain("scoring and publishing are skipped");
    }

    private BatchRunner runner(String kevUrl) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        NvdProperties nvdProperties = new NvdProperties("nvd-key", nvd.baseUrl(), 2000, Duration.ofDays(120),
                Duration.ZERO, 2, Duration.ZERO);
        NvdCollector nvdCollector = new NvdCollector(
                new NvdClient(nvdProperties, HttpClient.newHttpClient(), new ObjectMapper(), duration -> { }),
                new NvdCveMapper(new ObjectMapper()), vulnerabilities, clock);
        KevProperties kevProperties = new KevProperties(kevUrl, 1, Duration.ZERO, Duration.ofSeconds(5));
        KevCollector kevCollector = new KevCollector(new KevClient(kevProperties, duration -> { }),
                new KevCatalogParser(new ObjectMapper()), kevRepository, clock);
        ProductAssignService assignment = new ProductAssignService(
                new FakeProductAliasCatalog(ProductAliases.of(List.of())),
                new FakeProductAssignmentRepository(List.of(), List.of()));
        // 일부러 KEV 를 먼저 등록한다 — 실행 순서는 소스 순서(NVD → KEV)를 따라야 한다
        return new BatchRunner(new CollectService(List.of(kevCollector, nvdCollector), syncStates, clock), assignment,
                new ScoreService(trendScores, clock), new DailyStatService(dailyStats, clock),
                new SummaryService(new SummaryProperties(20, "", ""), Optional.empty(),
                        new FakeSummaryRepository(List.of(), 0), clock),
                SiteExports.writingTo(new FakeSiteDataWriter(), clock), FakePublisher.working(),
                GateChecks.failing(clock));
    }

    private static String closedUrl() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://127.0.0.1:" + socket.getLocalPort() + "/known_exploited_vulnerabilities.json";
        }
    }

    private static String kevCatalog() throws IOException {
        try (InputStream in = CollectionFailureRuleRunTest.class.getResourceAsStream("/kev/catalog-sample.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
