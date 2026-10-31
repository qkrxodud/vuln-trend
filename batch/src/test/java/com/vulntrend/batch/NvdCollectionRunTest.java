package com.vulntrend.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.application.collect.CollectOutcome;
import com.vulntrend.batch.application.collect.CollectService;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStatus;
import com.vulntrend.batch.global.config.NvdProperties;
import com.vulntrend.batch.infrastructure.nvd.NvdClient;
import com.vulntrend.batch.infrastructure.nvd.NvdCollector;
import com.vulntrend.batch.infrastructure.nvd.NvdCveMapper;
import com.vulntrend.batch.infrastructure.nvd.NvdSamples;
import com.vulntrend.batch.infrastructure.nvd.NvdStubServer;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import com.vulntrend.batch.infrastructure.persistence.syncstate.JdbcSyncStateRepository;
import com.vulntrend.batch.infrastructure.persistence.vulnerability.JdbcVulnerabilityRepository;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** WBS 1.4: NVD 수집을 수집 순회·진행 기록·취약점 목록과 함께 실제 저장 구조에서 돌린다. NVD 는 흉내 서버 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcVulnerabilityRepository.class, JdbcSyncStateRepository.class})
@DisplayName("NVD 수집 실행 — 실제 저장 구조에서")
class NvdCollectionRunTest {

    private static final Instant YESTERDAY = Instant.parse("2026-10-30T00:00:00Z");
    private static final Instant TODAY = Instant.parse("2026-10-31T00:00:00Z");
    private static final Instant TOMORROW = Instant.parse("2026-11-01T00:00:00Z");
    private static final List<String> DAY_OF_CHANGES =
            List.of(NvdSamples.log4shell(), NvdSamples.CHROME, NvdSamples.WORDPRESS, NvdSamples.REJECTED);

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
        // given: 어제 끝까지 가져온 NVD
        syncStates.save(SyncState.restore(SourceName.NVD, YESTERDAY, YESTERDAY, SyncStatus.SUCCESS));
    }

    @AfterEach
    void stopNvd() {
        nvd.close();
    }

    @Nested
    @DisplayName("같은 구간을 두 번 가져오면")
    class SameWindowTwice {

        @Test
        @DisplayName("취약점·영향받는 제품·참고 링크·원본이 두 번 올라가지 않는다")
        void noDuplicates() {
            // given
            nvd.pageOf(DAY_OF_CHANGES).pageOf(DAY_OF_CHANGES);

            // when: 같은 지금 시각으로 두 번 — 진행 기록을 되돌려 같은 구간을 다시 가져온다
            CollectOutcome first = run(TODAY);
            Map<String, Integer> afterFirst = counts();
            syncStates.save(SyncState.restore(SourceName.NVD, YESTERDAY, YESTERDAY, SyncStatus.SUCCESS));
            CollectOutcome second = run(TODAY);

            // then
            assertThat(first.hasFailure()).isFalse();
            assertThat(second.hasFailure()).isFalse();
            assertThat(counts()).isEqualTo(afterFirst);
            assertThat(afterFirst).containsEntry("vulnerability", 3)
                    .containsEntry("affected_product", 144)
                    .containsEntry("reference_link", 5)
                    .containsEntry("vulnerability_raw", 4);
            assertThat(nvd.received()).extracting(r -> r.param("lastModStartDate"))
                    .containsExactly("2026-10-30T00:00:00.000+00:00", "2026-10-30T00:00:00.000+00:00");
        }

        @Test
        @DisplayName("NVD 가 철회한 CVE 는 두 번 받아도 목록에 오르지 않는다")
        void rejectedNeverListed() {
            nvd.pageOf(DAY_OF_CHANGES).pageOf(DAY_OF_CHANGES);

            run(TODAY);
            run(TODAY);

            assertThat(jdbc.queryForObject("select count(*) from vulnerability where vuln_id = 'CVE-2026-30003'",
                    Integer.class)).isZero();
        }
    }

    @Nested
    @DisplayName("이어서 실행하면")
    class Consecutive {

        @Test
        @DisplayName("다 가져온 실행 뒤에는 다음 실행이 이번 구간 끝부터 이어 가져와 빈틈이 없다")
        void nextRunContinuesFromEnd() {
            nvd.pageOf(DAY_OF_CHANGES).pageOf(List.of(NvdSamples.CHROME));

            run(TODAY);
            run(TOMORROW);

            assertThat(nvd.received()).extracting(r -> r.param("lastModStartDate"))
                    .containsExactly("2026-10-30T00:00:00.000+00:00", "2026-10-31T00:00:00.000+00:00");
            assertThat(nvd.received()).extracting(r -> r.param("lastModEndDate"))
                    .containsExactly("2026-10-31T00:00:00.000+00:00", "2026-11-01T00:00:00.000+00:00");
            SyncState state = syncStates.findBySource(SourceName.NVD);
            assertThat(state.getLastSyncedAt()).contains(TOMORROW);
            assertThat(state.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        }

        @Test
        @DisplayName("NVD 수집이 실패하면 실패로 기록되고 다음 실행이 같은 구간을 다시 가져온다")
        void failedRunIsRetried() {
            // given: 첫 실행은 NVD 장애, 둘째 실행은 정상
            nvd.answer(503, "down").answer(503, "down").pageOf(DAY_OF_CHANGES);

            // when
            CollectOutcome failed = run(TODAY);
            SyncState afterFailure = syncStates.findBySource(SourceName.NVD);
            CollectOutcome retried = run(TOMORROW);

            // then
            assertThat(failed.failedSources()).containsExactly(SourceName.NVD);
            assertThat(afterFailure.getStatus()).isEqualTo(SyncStatus.FAIL);
            assertThat(afterFailure.getLastSyncedAt()).contains(YESTERDAY);
            assertThat(afterFailure.getLastRunAt()).contains(TODAY);
            assertThat(retried.hasFailure()).isFalse();
            assertThat(nvd.received().get(2).param("lastModStartDate")).isEqualTo("2026-10-30T00:00:00.000+00:00");
            assertThat(syncStates.findBySource(SourceName.NVD).getLastSyncedAt()).contains(TOMORROW);
        }
    }

    private CollectOutcome run(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        NvdProperties properties = new NvdProperties("nvd-key", nvd.baseUrl(), 2000, Duration.ofDays(120),
                Duration.ZERO, 2, Duration.ZERO);
        NvdClient client = new NvdClient(properties, HttpClient.newHttpClient(), new ObjectMapper(), duration -> { });
        NvdCollector collector = new NvdCollector(client, new NvdCveMapper(new ObjectMapper()), vulnerabilities, clock);
        return new CollectService(List.of(collector), syncStates, clock).collectAll();
    }

    private Map<String, Integer> counts() {
        return Map.of(
                "vulnerability", count("vulnerability"),
                "affected_product", count("affected_product"),
                "reference_link", count("reference_link"),
                "vulnerability_raw", count("vulnerability_raw"));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
