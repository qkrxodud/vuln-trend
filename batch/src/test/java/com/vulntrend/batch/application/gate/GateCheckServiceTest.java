package com.vulntrend.batch.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.export.dto.SearchListing;
import com.vulntrend.batch.domain.gate.GateItem;
import com.vulntrend.batch.domain.gate.GateReport;
import com.vulntrend.batch.domain.gate.GateStatus;
import com.vulntrend.batch.domain.remedy.dto.RemedyListing;
import com.vulntrend.batch.domain.score.ScoreBreakdown;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.Severity;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStatus;
import com.vulntrend.batch.fake.FakeCveDetailRepository;
import com.vulntrend.batch.fake.FakeDocumentSizer;
import com.vulntrend.batch.fake.FakeGateQueryRepository;
import com.vulntrend.batch.fake.FakeRepositoryHistory;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import com.vulntrend.batch.fake.FakeSyncStateRepository;
import com.vulntrend.batch.fake.FakeTrendListRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Gate G 배치 쪽 점검")
class GateCheckServiceTest {

    /** 한국 시각 2026-11-03 10:00 */
    private static final Instant NOW = Instant.parse("2026-11-03T01:00:00Z");
    private static final Instant RAN = Instant.parse("2026-11-03T00:00:00Z");

    private final FakeRepositoryHistory history = new FakeRepositoryHistory()
            .commit("2026-11-03T09:00", 40).commit("2026-11-02T09:00", 20).commit("2026-11-01T09:00", 30)
            .size(100L * 1024 * 1024, 0);
    private final FakeSyncStateRepository syncStates = new FakeSyncStateRepository();
    private final FakeGateQueryRepository gateQueries = new FakeGateQueryRepository();
    private final FakeSiteDataWriter files = new FakeSiteDataWriter().sized("search-index.json", 12L * 1024 * 1024,
            2L * 1024 * 1024);
    private final FakeCveDetailRepository details = new FakeCveDetailRepository()
            .search(new SearchListing(CveId.from("CVE-2026-50001"), "새 건", null), Instant.parse("2026-10-30T00:00:00Z"))
            .search(new SearchListing(CveId.from("CVE-2026-20001"), "넉 달 전", null),
                    Instant.parse("2026-07-01T00:00:00Z"))
            .search(new SearchListing(CveId.from("CVE-2025-90001"), "열 달 전", null),
                    Instant.parse("2026-01-01T00:00:00Z"));
    private final FakeTrendListRepository trends = new FakeTrendListRepository();

    @BeforeEach
    void sources() {
        for (SourceName source : new SourceName[] {SourceName.NVD, SourceName.KEV, SourceName.EPSS}) {
            syncStates.save(SyncState.restore(source, RAN, RAN, SyncStatus.SUCCESS));
        }
        for (int rank = 1; rank <= 12; rank++) {
            trends.with(listing("CVE-2026-" + (41200 + rank)));
        }
    }

    @Test
    @DisplayName("다섯 항목을 정해진 순서로 판정한다")
    void fiveItemsInOrder() {
        GateReport report = service().check();

        assertThat(report.getItems()).extracting(GateItem::getItem).containsExactly("배치 3일 연속 성공",
                "미매핑 대표 제품", "레포 크기·커밋당 변경 파일 수", "search-index 크기", "상위 10건 점수 구성");
        assertThat(report.getItems()).extracting(GateItem::getStatus).containsExactly(GateStatus.PASS,
                GateStatus.PASS, GateStatus.REVIEW, GateStatus.PASS, GateStatus.REVIEW);
        assertThat(report.hasFailure()).isFalse();
    }

    @Test
    @DisplayName("search-index 는 파일이 4MB 를 넘어도 gzip 크기로 판정해 통과한다")
    void searchIndexByGzip() {
        GateItem item = service().check().getItems().get(3);

        assertThat(item.getStatus()).isEqualTo(GateStatus.PASS);
        assertThat(item.getDetail()).isEqualTo("gzip 2.00MB ≤ 4MB (파일 12.00MB)");
    }

    @Test
    @DisplayName("제품 표에 없는 대표 제품은 트렌드 상위 100건에서 찾는다")
    void unmappedAmongTopHundred() {
        service().check();

        assertThat(gateQueries.askedTop()).isEqualTo(100);
    }

    @Nested
    @DisplayName("search-index 크기 예상은")
    class Estimates {

        @Test
        @DisplayName("전체와 최근 12·9·6개월을 지금 모양과 줄바꿈 없는 모양으로 잰다")
        void scopes() {
            GateItem item = service().check().getItems().get(3);

            assertThat(item.getEvidence()).containsExactly(
                    "전체: 3건, 지금 모양 0.00MB, 줄바꿈 없이 0.00MB, gzip 0.00MB",
                    "최근 12개월: 3건, 지금 모양 0.00MB, 줄바꿈 없이 0.00MB, gzip 0.00MB",
                    "최근 9개월: 2건, 지금 모양 0.00MB, 줄바꿈 없이 0.00MB, gzip 0.00MB",
                    "최근 6개월: 2건, 지금 모양 0.00MB, 줄바꿈 없이 0.00MB, gzip 0.00MB");
        }
    }

    @Test
    @DisplayName("상위 10건은 순위·점수·일곱 항·왜 지금?·KEV·악용 확률을 한 줄씩 보여 준다")
    void topTenLines() {
        GateItem item = service().check().getItems().get(4);

        assertThat(item.getEvidence()).hasSize(10);
        assertThat(item.getEvidence().get(0)).isEqualTo("1. CVE-2026-41201 67점 = KEV 50.0 · 랜섬 0.0 · EPSS 28.3 · "
                + "뉴스 0.0 · PoC 0.0 · CVSS 9.8 · 감쇠 -21.3 — CISA 악용 목록 등재 — KEV(랜섬 아님) · EPSS 0.94310 "
                + "(백분위 0.99900) · CVSS 9.8");
    }

    @Nested
    @DisplayName("일부러 실패시키면")
    class Failing {

        @Test
        @DisplayName("데이터 커밋이 하루 비면 3일 연속 항목이 실패하고 전체가 실패로 끝난다")
        void gapFails() {
            FakeRepositoryHistory gap = new FakeRepositoryHistory().commit("2026-11-03T09:00", 1)
                    .commit("2026-11-01T09:00", 1).commit("2026-10-31T09:00", 1);

            GateReport report = service(gap, files).check();

            assertThat(report.getItems().get(0).getStatus()).isEqualTo(GateStatus.FAIL);
            assertThat(report.hasFailure()).isTrue();
        }

        @Test
        @DisplayName("git 기록을 읽지 못하면 git 을 보는 두 항목을 실패로 적고 나머지는 계속 판정한다")
        void unreadableGit() {
            GateReport report = service(new FakeRepositoryHistory().unreadable(), files).check();

            assertThat(report.getItems().get(0).getStatus()).isEqualTo(GateStatus.FAIL);
            assertThat(report.getItems().get(0).getDetail()).contains("git 기록을 읽지 못함");
            assertThat(report.getItems().get(2).getStatus()).isEqualTo(GateStatus.FAIL);
            assertThat(report.getItems().get(3).getStatus()).isEqualTo(GateStatus.PASS);
        }

        @Test
        @DisplayName("저장소 크기만 읽지 못하면 레포 크기 항목만 실패하고 3일 연속 항목은 판정한다")
        void sizeUnreadable() {
            history.sizeUnreadable();

            GateReport report = service().check();

            assertThat(report.getItems().get(0).getStatus()).isEqualTo(GateStatus.PASS);
            assertThat(report.getItems().get(2).getStatus()).isEqualTo(GateStatus.FAIL);
        }

        @Test
        @DisplayName("search-index 의 gzip 크기가 4MB 를 넘으면 그 항목이 실패한다")
        void bigSearchIndex() {
            FakeSiteDataWriter big = new FakeSiteDataWriter().sized("search-index.json", 40L * 1024 * 1024,
                    5L * 1024 * 1024);

            assertThat(service(history, big).check().getItems().get(3).getStatus()).isEqualTo(GateStatus.FAIL);
        }

        @Test
        @DisplayName("제품 표에 없는 대표 제품이 있으면 그 항목이 확인 필요가 되고 실패로 치지는 않는다")
        void unmappedIsReview() {
            gateQueries.unmapped("ivanti", "connect_secure", 4);

            GateReport report = service().check();

            assertThat(report.getItems().get(1).getStatus()).isEqualTo(GateStatus.REVIEW);
            assertThat(report.hasFailure()).isFalse();
        }
    }

    private GateCheckService service() {
        return service(history, files);
    }

    private GateCheckService service(FakeRepositoryHistory repositoryHistory, FakeSiteDataWriter siteFiles) {
        return new GateCheckService(repositoryHistory, syncStates, gateQueries, siteFiles, new FakeDocumentSizer(),
                details, trends, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TrendListing listing(String id) {
        ScoreBreakdown breakdown = ScoreBreakdown.of(new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("28.293"),
                new BigDecimal("9.8"), new BigDecimal("0.757858283255199"));
        return new TrendListing(CveId.from(id), "제목", breakdown, true, false, LocalDate.parse("2026-11-10"),
                LocalDate.parse("2026-10-20"), 0, 0,
                new BigDecimal("0.94310"), new BigDecimal("0.99900"), new BigDecimal("9.8"), "3.1", Severity.CRITICAL,
                "Chrome", null, Instant.parse("2026-10-20T00:00:00Z"),
                new RemedyListing(List.of(), List.of(), null), null, List.of());
    }
}
