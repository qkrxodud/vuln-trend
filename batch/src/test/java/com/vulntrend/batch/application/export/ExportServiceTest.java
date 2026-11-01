package com.vulntrend.batch.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.application.stat.KpiService;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.fake.FakeCveDetailRepository;
import com.vulntrend.batch.fake.FakeDailyStatRepository;
import com.vulntrend.batch.fake.FakeKpiRepository;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import com.vulntrend.batch.fake.FakeTrendListRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("사이트 데이터 내보내기")
class ExportServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    private final FakeSiteDataWriter writer = new FakeSiteDataWriter();
    private final FakeKpiRepository kpi = new FakeKpiRepository();
    private final FakeTrendListRepository trends = new FakeTrendListRepository()
            .with(TrendListings.kev("CVE-2026-41203"))
            .with(TrendListings.unrated("CVE-2026-50001"));
    private final FakeCveDetailRepository details = new FakeCveDetailRepository()
            .detail(TrendListings.kevDetail("CVE-2026-41203"))
            .detail(TrendListings.bareDetail("CVE-2026-50001"))
            .detail(TrendListings.bareDetail("CVE-2026-60001"))
            .top("CVE-2026-41203", "CVE-2026-60001");

    @Test
    @DisplayName("상세 파일은 index·recent 에 실린 CVE 와 점수 상위 1000건에 대해서만 만든다")
    void detailScope() {
        service().exportAll();

        assertThat(details.askedTop()).isEqualTo(1000);
        assertThat(details.askedDetails()).containsExactly(CveId.from("CVE-2026-41203"), CveId.from("CVE-2026-50001"),
                CveId.from("CVE-2026-60001"));
    }

    @Test
    @DisplayName("최근 7일 목록의 CVE 는 몇 건이든 모두 상세 파일을 만든다")
    void detailForEveryRecentCve() {
        FakeTrendListRepository listed = new FakeTrendListRepository().with(TrendListings.kev("CVE-2026-41203"));
        IntStream.rangeClosed(1, 150).forEach(i -> listed.withRecent(
                TrendListings.unrated("CVE-2026-%05d".formatted(50000 + i))));

        service(listed).exportAll();

        assertThat(details.askedDetails()).hasSize(1 + 150 + 1).contains(CveId.from("CVE-2026-50150"));
    }

    @Test
    @DisplayName("목록 파일과 상세 파일을 다 쓴 뒤 마지막에 meta.json 을 써서 판 이름이 가장 늦게 바뀐다")
    void metaLast() {
        service().exportAll();

        assertThat(writer.order()).startsWith("stats.json", "index.json", "recent.json", "search-index.json");
        assertThat(writer.order()).endsWith("meta.json");
        assertThat(writer.order()).contains("cve/CVE-2026-41203.json", "cve/CVE-2026-50001.json",
                "cve/CVE-2026-60001.json");
    }

    @Test
    @DisplayName("모든 파일이 제품 표의 표시 이름을 보고 같은 규칙으로 제품 이름을 쓴다")
    void productNamesFromTable() {
        trends.with(TrendListings.kevWithProduct("CVE-2026-70001", "curl"))
                .with(TrendListings.kevWithProduct("CVE-2026-70002", "sql_server"));
        details.standardNames("curl");

        service().exportAll();

        assertThat((List<?>) writer.documents().get("index.json")).map(TrendRowJson.class::cast)
                .extracting(TrendRowJson::primaryProduct).contains("curl", "SQL Server");
    }

    @Test
    @DisplayName("쓴 수·그대로 둔 수·지운 수·상세 파일 수를 알린다")
    void result() {
        writer.existing("cve/CVE-2021-44228.json");

        ExportResult result = service().exportAll();

        assertThat(result.getWritten()).isEqualTo(8);
        assertThat(result.getUnchanged()).isZero();
        assertThat(result.getRemoved()).isEqualTo(1);
        assertThat(result.getDetailFiles()).isEqualTo(3);
        assertThat(result.hasChanges()).isTrue();
    }

    @Test
    @DisplayName("다시 내보내도 바뀐 것이 없으면 바뀐 파일이 없다고 알린다")
    void nothingChanged() {
        ExportService service = service();
        service.exportAll();

        ExportResult again = service.exportAll();

        assertThat(again.getWritten()).isZero();
        assertThat(again.getUnchanged()).isEqualTo(8);
        assertThat(again.hasChanges()).isFalse();
    }

    @Nested
    @DisplayName("게시할지 정하는 화면 지문은")
    class Fingerprint {

        @Test
        @DisplayName("파일을 하나도 쓰지 않고 계산하며, 같은 데이터로 내보낸 결과의 지문과 같다")
        void withoutWriting() {
            String fingerprint = service().visibleFingerprint();

            assertThat(writer.order()).isEmpty();
            assertThat(service().exportAll().getFingerprint()).isEqualTo(fingerprint).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("실행 시각만 다르고 화면 값이 같으면 같다 — 갱신 시각·판 이름은 넣지 않는다")
        void sameScreenLaterRun() {
            String earlier = service().exportAll().getFingerprint();

            String later = service(Clock.fixed(NOW.plusSeconds(2 * 3600), ZoneOffset.UTC)).visibleFingerprint();

            assertThat(later).isEqualTo(earlier);
        }

        @Test
        @DisplayName("최근 7일 목록에만 새 CVE 가 들어오면 그대로다 — 새 CVE 는 계속 들어오므로 최근 목록은 지문에 넣지 않는다")
        void recentOnlyKeepsIt() {
            FakeTrendListRepository listed = new FakeTrendListRepository()
                    .with(TrendListings.kev("CVE-2026-41203"))
                    .withRecent(TrendListings.unrated("CVE-2026-50001"));
            String before = service(listed).visibleFingerprint();
            listed.withRecent(TrendListings.unrated("CVE-2026-50002"));

            assertThat(service(listed).visibleFingerprint()).isEqualTo(before);
        }

        @Test
        @DisplayName("주목 목록 줄의 제목이 바뀌면 달라진다")
        void titleChangesIt() {
            FakeTrendListRepository titled = new FakeTrendListRepository()
                    .with(TrendListings.kevTitled("CVE-2026-41203", "Chrome V8 타입 혼동으로 원격 코드 실행"))
                    .with(TrendListings.unrated("CVE-2026-50001"));

            assertThat(service(titled).visibleFingerprint()).isNotEqualTo(service().visibleFingerprint());
        }

        @Test
        @DisplayName("보이는 제품 이름이 바뀌면 달라진다 — 표에 이름이 생겨 원문 대신 표의 이름이 보일 때")
        void shownProductNameChangesIt() {
            trends.with(TrendListings.kevWithProduct("CVE-2026-70001", "curl"));
            String readable = service().visibleFingerprint();
            details.standardNames("curl");

            assertThat(service().visibleFingerprint()).isNotEqualTo(readable);
        }

        @Test
        @DisplayName("최근 7일·그 전 7일 KEV 등재 수가 바뀌면 달라진다")
        void kevWeeksChangeIt() {
            String before = service().visibleFingerprint();
            kpi.kevAddedOn(LocalDate.parse("2026-10-31"), 1);

            assertThat(service().visibleFingerprint()).isNotEqualTo(before);
        }

        @Test
        @DisplayName("주목 목록에 새 줄이 들어오면 달라진다")
        void newRowChangesIt() {
            String before = service().visibleFingerprint();
            trends.with(TrendListings.kev("CVE-2026-70001"));

            assertThat(service().visibleFingerprint()).isNotEqualTo(before);
        }
    }

    @Nested
    @DisplayName("검색 색인은")
    class SearchIndex {

        @Test
        @DisplayName("다시 만들지 않는 실행이면 있던 파일을 그대로 두고 그대로 둔 파일로 센다")
        void keptWhenNotRebuilding() {
            writer.existing("search-index.json");

            ExportResult result = service().exportAll(false);

            assertThat(writer.order()).doesNotContain("search-index.json");
            assertThat(result.getUnchanged()).isEqualTo(1);
        }

        @Test
        @DisplayName("다시 만들지 않는 실행이어도 파일이 없으면 쓴다")
        void writtenWhenMissing() {
            service().exportAll(false);

            assertThat(writer.order()).contains("search-index.json");
        }

        @Test
        @DisplayName("한국 날짜의 첫 게시처럼 다시 만드는 실행이면 있던 파일도 새로 쓴다")
        void rebuilt() {
            writer.existing("search-index.json");

            service().exportAll(true);

            assertThat(writer.order()).contains("search-index.json");
        }
    }

    private ExportService service() {
        return service(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ExportService service(Clock clock) {
        return service(clock, trends);
    }

    private ExportService service(FakeTrendListRepository listed) {
        return service(Clock.fixed(NOW, ZoneOffset.UTC), listed);
    }

    private ExportService service(Clock clock, FakeTrendListRepository listed) {
        FakeCveDetailRepository repository = details;
        return new ExportService(new StatsFileExport(new FakeDailyStatRepository(), writer),
                new IndexFileExport(listed, writer), new RecentFileExport(listed, writer),
                new SearchIndexFileExport(repository, writer), new CveDetailFileExport(repository, writer),
                new MetaFileExport(new KpiService(kpi), writer), repository,
                clock);
    }
}
