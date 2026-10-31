package com.vulntrend.batch.application.gate;

import com.vulntrend.batch.application.export.SearchIndexFileExport;
import com.vulntrend.batch.application.export.TrendRows;
import com.vulntrend.batch.application.export.dto.ScoreBreakdownJson;
import com.vulntrend.batch.application.export.dto.TrendRowJson;
import com.vulntrend.batch.domain.export.CveDetailRepository;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.DocumentSizer;
import com.vulntrend.batch.domain.export.SiteDataWriter;
import com.vulntrend.batch.domain.export.dto.SearchListing;
import com.vulntrend.batch.domain.gate.DailyRunCheck;
import com.vulntrend.batch.domain.gate.GateItem;
import com.vulntrend.batch.domain.gate.GateQueryRepository;
import com.vulntrend.batch.domain.gate.GateReport;
import com.vulntrend.batch.domain.gate.GateStatus;
import com.vulntrend.batch.domain.gate.ProductMappingCheck;
import com.vulntrend.batch.domain.gate.RepoGrowthCheck;
import com.vulntrend.batch.domain.gate.RepositoryHistory;
import com.vulntrend.batch.domain.gate.SearchIndexSizeCheck;
import com.vulntrend.batch.domain.gate.dto.DataCommit;
import com.vulntrend.batch.domain.gate.dto.SizeEstimate;
import com.vulntrend.batch.domain.score.TrendListRepository;
import com.vulntrend.batch.domain.stat.StatDays;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStateRepository;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Gate G 배치 쪽 점검(WBS 9장). 웹의 npm run gate:check 와 짝이다 — 같은 표 모양, 실패가 있으면 종료 코드 1, 확인 필요는 사람이 판단.
 * 항목: ① 배치 3일 연속 성공 ② 미매핑 대표 제품(상위 100) ③ 레포 크기·커밋당 변경 파일 수 ④ search-index 크기(R8)와 줄이는 방법별 예상
 * ⑤ 상위 10건 점수 구성(가중치 조정 판단용). 읽기만 하고 아무것도 쓰지 않는다. git 을 읽지 못하면 ①·③ 만 실패로 적고 나머지는 계속 판정한다.
 */
@Service
@RequiredArgsConstructor
public class GateCheckService {

    private static final int COMMITS = 30;
    private static final int UNMAPPED_TOP = 100;
    private static final int TOP_TEN = 10;
    private static final List<Integer> MONTHS = List.of(12, 9, 6);
    private static final DataFilePath SEARCH_INDEX = DataFilePath.of("search-index.json");
    private static final String UNREADABLE_GIT = "git 기록을 읽지 못함";

    private final RepositoryHistory history;
    private final SyncStateRepository syncStates;
    private final GateQueryRepository gateQueries;
    private final SiteDataWriter files;
    private final DocumentSizer sizer;
    private final CveDetailRepository details;
    private final TrendListRepository trends;
    private final Clock clock;

    public GateReport check() {
        Instant now = clock.instant();
        Optional<List<DataCommit>> commits = readCommits();
        List<SyncState> states = DailyRunCheck.MVP_SOURCES.stream().map(syncStates::findBySource).toList();
        return GateReport.of(List.of(
                commits.map(found -> DailyRunCheck.judge(found, states, now))
                        .orElseGet(() -> GateItem.of(DailyRunCheck.ITEM, GateStatus.FAIL, UNREADABLE_GIT)),
                ProductMappingCheck.judge(gateQueries.unmappedProducts(UNMAPPED_TOP),
                        gateQueries.withoutProduct(UNMAPPED_TOP), UNMAPPED_TOP),
                repoGrowth(commits),
                SearchIndexSizeCheck.judge(files.sizeOf(SEARCH_INDEX), estimates(now)),
                topTen(now)));
    }

    private Optional<List<DataCommit>> readCommits() {
        try {
            return Optional.of(history.recentDataCommits(COMMITS));
        } catch (SourceFetchException unreadable) {
            return Optional.empty();
        }
    }

    private GateItem repoGrowth(Optional<List<DataCommit>> commits) {
        try {
            return commits.map(found -> RepoGrowthCheck.judge(history.size(), found))
                    .orElseGet(() -> GateItem.of(RepoGrowthCheck.ITEM, GateStatus.FAIL, UNREADABLE_GIT));
        } catch (SourceFetchException unreadable) {
            return GateItem.of(RepoGrowthCheck.ITEM, GateStatus.FAIL, UNREADABLE_GIT);
        }
    }

    private List<SizeEstimate> estimates(Instant now) {
        List<SizeEstimate> estimates = new ArrayList<>(List.of(estimate("전체", details.searchEntries())));
        MONTHS.forEach(months -> estimates.add(estimate("최근 " + months + "개월",
                details.searchEntriesSince(now.atZone(StatDays.CALENDAR).minusMonths(months).toInstant()))));
        return estimates;
    }

    /** 실제로 쓰는 문서 모양(SearchIndexFileExport 와 같은 줄)으로 잰다 */
    private SizeEstimate estimate(String scope, List<SearchListing> entries) {
        List<?> document = entries.stream().map(SearchIndexFileExport::entry).toList();
        return new SizeEstimate(scope, entries.size(), sizer.prettyBytes(document), sizer.compactBytes(document));
    }

    private GateItem topTen(Instant now) {
        List<TrendRowJson> rows = trends.top(TOP_TEN, now).stream().map(TrendRows::from).toList();
        List<String> lines = IntStream.range(0, rows.size()).mapToObj(i -> line(i + 1, rows.get(i))).toList();
        return GateItem.of("상위 10건 점수 구성", GateStatus.REVIEW, "가중치 조정 판단용 — " + rows.size() + "건", lines);
    }

    private static String line(int rank, TrendRowJson row) {
        ScoreBreakdownJson b = row.scoreBreakdown();
        return rank + ". " + row.id() + " " + row.score() + "점 = KEV " + b.kev() + " · 랜섬 " + b.ransomware()
                + " · EPSS " + b.epss() + " · 뉴스 " + b.news() + " · PoC " + b.poc() + " · CVSS " + b.cvss()
                + " · 감쇠 " + b.decay() + " — " + row.whyNow() + " — "
                + (row.isKev() ? "KEV(" + (row.kevRansomware() ? "랜섬" : "랜섬 아님") + ")" : "KEV 아님")
                + " · EPSS " + text(row.epss()) + " (백분위 " + text(row.epssPercentile()) + ") · CVSS "
                + text(row.cvss());
    }

    private static String text(BigDecimal value) {
        return Optional.ofNullable(value).map(BigDecimal::toPlainString).orElse("없음");
    }
}
