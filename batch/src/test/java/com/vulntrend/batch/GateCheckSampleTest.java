package com.vulntrend.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.SearchIndexFileExport;
import com.vulntrend.batch.application.gate.GateCheckService;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.gate.GateItem;
import com.vulntrend.batch.domain.gate.GateReport;
import com.vulntrend.batch.domain.gate.GateStatus;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStatus;
import com.vulntrend.batch.fake.FakeRepositoryHistory;
import com.vulntrend.batch.fake.FakeSyncStateRepository;
import com.vulntrend.batch.global.config.OutputProperties;
import com.vulntrend.batch.infrastructure.export.JsonFileWriter;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import com.vulntrend.batch.infrastructure.persistence.score.JdbcCveDetailRepository;
import com.vulntrend.batch.infrastructure.persistence.score.JdbcGateQueryRepository;
import com.vulntrend.batch.infrastructure.persistence.score.JdbcTrendListRepository;
import com.vulntrend.batch.infrastructure.persistence.score.JdbcTrendScoreRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Gate G 점검을 실데이터 표본(score/ranking-sample.csv, 3.5)과 실제 저장 구조로 돌린다. git 기록과 소스 진행 기록만 흉내 낸다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcTrendScoreRepository.class, JdbcTrendListRepository.class,
        JdbcCveDetailRepository.class, JdbcGateQueryRepository.class})
@DisplayName("Gate G 배치 쪽 점검 — 실데이터 표본에서")
class GateCheckSampleTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Autowired
    private JdbcTrendScoreRepository scores;

    @Autowired
    private JdbcTrendListRepository trends;

    @Autowired
    private JdbcCveDetailRepository details;

    @Autowired
    private JdbcGateQueryRepository gateQueries;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    private Path dataDir;

    private JsonFileWriter files;

    @BeforeEach
    void sample() throws IOException {
        List<Object[]> rows = lines().stream().skip(1).map(GateCheckSampleTest::row).toList();
        jdbc.batchUpdate("insert into vulnerability (vuln_id, published_at, cvss_score, cvss_severity, is_kev, "
                + "kev_added_at, kev_ransomware, epss_score, epss_percentile) "
                + "values (?, cast(? as timestamptz), ?, ?, ?, cast(? as date), ?, ?, ?)", rows);
        scores.rescoreAll(NOW);
        jdbc.update("insert into product_alias (cpe_vendor, cpe_product, slug, display_name) values "
                + "('microsoft', 'windows', 'windows', 'Windows')");
        jdbc.update("update vulnerability set primary_product = 'Windows' where vuln_id = 'CVE-2026-109618'");
        jdbc.update("update vulnerability set primary_product = 'connect_secure' where vuln_id in "
                + "('CVE-2026-109619', 'CVE-2026-111497')");
        files = new JsonFileWriter(new OutputProperties(dataDir.toString()));
    }

    @Test
    @DisplayName("상위 10건 첫 줄은 표본 1위(10-26 KEV 등재, 80점)이고 제품 표에 없는 대표 제품은 후보로 나온다")
    void topTenAndUnmapped() {
        GateReport report = service().check();

        GateItem topTen = report.getItems().get(4);
        assertThat(topTen.getEvidence()).hasSize(10);
        assertThat(topTen.getEvidence().get(0)).startsWith("1. CVE-2026-109618 80점 = KEV 50.0");
        GateItem unmapped = report.getItems().get(1);
        assertThat(unmapped.getStatus()).isEqualTo(GateStatus.REVIEW);
        assertThat(unmapped.getEvidence()).containsExactly("?:connect_secure — 2건");
        assertThat(unmapped.getDetail()).contains("제품 없음 97건");
    }

    @Test
    @DisplayName("search-index 를 아직 쓰지 않았으면 크기 항목이 실패하고 전체가 실패로 끝난다")
    void missingSearchIndexFails() {
        GateReport report = service().check();

        assertThat(report.getItems().get(3).getStatus()).isEqualTo(GateStatus.FAIL);
        assertThat(report.hasFailure()).isTrue();
    }

    @Test
    @DisplayName("search-index 를 쓰면 실제 파일의 gzip 크기로 판정하고 기간별 예상을 함께 남긴다")
    void writtenSearchIndexMeasured() {
        new SearchIndexFileExport(details, files).write(NOW);

        GateItem size = service().check().getItems().get(3);

        assertThat(size.getStatus()).isEqualTo(GateStatus.PASS);
        assertThat(size.getEvidence()).hasSize(4);
        assertThat(size.getEvidence().get(0)).startsWith("전체: 1771건");
        assertThat(size.getEvidence().get(3)).startsWith("최근 6개월: ");
    }

    private GateCheckService service() {
        FakeSyncStateRepository syncStates = new FakeSyncStateRepository();
        for (SourceName source : new SourceName[] {SourceName.NVD, SourceName.KEV, SourceName.EPSS}) {
            syncStates.save(SyncState.restore(source, NOW, NOW, SyncStatus.SUCCESS));
        }
        FakeRepositoryHistory history = new FakeRepositoryHistory().commit("2026-10-31T09:00", 1200)
                .commit("2026-10-30T09:00", 900).commit("2026-10-29T09:00", 950).size(80L * 1024 * 1024, 0);
        return new GateCheckService(history, syncStates, gateQueries, files, files, details, trends,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static List<String> lines() throws IOException {
        try (InputStream in = GateCheckSampleTest.class.getResourceAsStream("/score/ranking-sample.csv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(Predicate.not(line -> line.startsWith("#")))
                    .toList();
        }
    }

    private static Object[] row(String line) {
        String[] cells = line.split(",", -1);
        return new Object[] {cells[0], blank(cells[1]), decimal(cells[2]), cells[3], Boolean.parseBoolean(cells[4]),
                blank(cells[5]), Boolean.parseBoolean(cells[6]), decimal(cells[7]), decimal(cells[8])};
    }

    private static String blank(String cell) {
        return cell.isEmpty() ? null : cell;
    }

    private static BigDecimal decimal(String cell) {
        return Optional.ofNullable(blank(cell)).map(BigDecimal::new).orElse(null);
    }
}
