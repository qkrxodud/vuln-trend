package com.vulntrend.batch.contract;

import static com.vulntrend.batch.contract.MockShapes.assertSameShape;

import com.vulntrend.batch.application.export.CveDetailFileExport;
import com.vulntrend.batch.application.export.IndexFileExport;
import com.vulntrend.batch.application.export.MetaFileExport;
import com.vulntrend.batch.application.export.RecentFileExport;
import com.vulntrend.batch.application.export.SearchIndexFileExport;
import com.vulntrend.batch.application.export.StatsFileExport;
import com.vulntrend.batch.application.stat.KpiService;
import com.vulntrend.batch.domain.export.dto.AffectedListing;
import com.vulntrend.batch.domain.export.dto.CveDetailListing;
import com.vulntrend.batch.domain.export.dto.ReferenceListing;
import com.vulntrend.batch.domain.export.dto.SearchListing;
import com.vulntrend.batch.domain.export.dto.SummaryListing;
import com.vulntrend.batch.domain.product.ProductNames;
import com.vulntrend.batch.domain.score.ScoreBreakdown;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.stat.DailyStat;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.Severity;
import com.vulntrend.batch.fake.FakeCveDetailRepository;
import com.vulntrend.batch.fake.FakeDailyStatRepository;
import com.vulntrend.batch.fake.FakeKpiRepository;
import com.vulntrend.batch.fake.FakeTrendListRepository;
import com.vulntrend.batch.global.config.OutputProperties;
import com.vulntrend.batch.infrastructure.export.JsonFileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("사이트 데이터 파일 모양 — 프론트 목업과 같다")
class MockShapeTest {

    private static final ProductNames NAMES = ProductNames.of(List.of());

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @TempDir
    private Path dataDir;

    private JsonFileWriter writer;

    @BeforeEach
    void writer() {
        writer = new JsonFileWriter(new OutputProperties(dataDir.toString()));
    }

    @Test
    @DisplayName("meta.json 은 갱신 시각·판·지금 창·어제 창 KPI 4개를 같은 이름으로 싣는다")
    void meta() throws IOException {
        FakeKpiRepository kpi = new FakeKpiRepository().highRiskNow(318)
                .highRiskRecorded(LocalDate.parse("2026-10-30"), 313);

        new MetaFileExport(new KpiService(kpi), writer).write(NOW);

        assertSameShape(dataDir.resolve("meta.json"), "meta.json");
    }

    @Test
    @DisplayName("stats.json 은 14일 막대와 7일 심각도 분포를 같은 이름으로 싣는다")
    void stats() throws IOException {
        FakeDailyStatRepository stats = new FakeDailyStatRepository()
                .with(DailyStat.of(LocalDate.parse("2026-10-30"), 142, 11, 40, 50, 10, 31));

        new StatsFileExport(stats, writer).write(NOW);

        assertSameShape(dataDir.resolve("stats.json"), "stats.json");
    }

    @Test
    @DisplayName("index.json 과 recent.json 의 줄은 같은 이름으로 점수 구성까지 싣는다")
    void indexAndRecent() throws IOException {
        FakeTrendListRepository trends = new FakeTrendListRepository().with(listing("CVE-2026-41203", true))
                .with(listing("CVE-2026-50001", false));

        new IndexFileExport(trends, writer).write(NOW, NAMES);
        new RecentFileExport(trends, writer).write(NOW, NAMES);

        assertSameShape(dataDir.resolve("index.json"), "index.json");
        assertSameShape(dataDir.resolve("recent.json"), "recent.json");
    }

    @Test
    @DisplayName("search-index.json 은 번호·제목·제품을 같은 이름으로 싣는다")
    void searchIndex() throws IOException {
        FakeCveDetailRepository repository = new FakeCveDetailRepository()
                .search(new SearchListing(CveId.from("CVE-2026-41203"), "제목", "Chrome"), NOW);

        new SearchIndexFileExport(repository, writer).write(NOW, NAMES);

        assertSameShape(dataDir.resolve("search-index.json"), "search-index.json");
    }

    @Test
    @DisplayName("상세 파일은 목록 줄 필드 전부와 상세 전용 필드를 목업과 같은 이름·순서로 싣는다")
    void cveDetail() throws IOException {
        CveDetailListing detail = new CveDetailListing(listing("CVE-2026-41203", true), "Type confusion in V8.",
                NOW, LocalDate.parse("2026-10-29"),
                new SummaryListing("무엇", "누가", "조치", "claude-haiku-5-5", NOW),
                List.of(new AffectedListing("google", "chrome", "Chrome", "chrome", null, "< 130", null)),
                List.of(new ReferenceListing("https://vendor.example/a", "vendor", null)));

        new CveDetailFileExport(new FakeCveDetailRepository().detail(detail), writer)
                .write(List.of(CveId.from("CVE-2026-41203")), NOW, NAMES);

        assertSameShape(dataDir.resolve("cve/CVE-2026-41203.json"), "cve/CVE-2026-41203.json");
    }

    private static TrendListing listing(String id, boolean kev) {
        ScoreBreakdown breakdown = ScoreBreakdown.of(new BigDecimal(kev ? "50" : "0"), BigDecimal.ZERO,
                new BigDecimal("28.293"), new BigDecimal("9.8"), new BigDecimal("0.75"));
        return new TrendListing(CveId.from(id), "제목", breakdown, kev, false, kev ? LocalDate.parse("2026-11-03") : null,
                0, 0, new BigDecimal("0.94310"), new BigDecimal("0.99900"), new BigDecimal("9.8"), "3.1",
                Severity.CRITICAL, "Chrome", kev ? "요약" : null, NOW);
    }
}
