package com.vulntrend.batch.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.collect.CollectService;
import com.vulntrend.batch.application.product.ProductAssignService;
import com.vulntrend.batch.application.score.ScoreService;
import com.vulntrend.batch.application.stat.DailyStatService;
import com.vulntrend.batch.application.summary.SummaryService;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.product.ProductAliases;
import com.vulntrend.batch.fake.FakeCollector;
import com.vulntrend.batch.fake.FakeDailyStatRepository;
import com.vulntrend.batch.fake.FakeProductAliasCatalog;
import com.vulntrend.batch.fake.FakeProductAssignmentRepository;
import com.vulntrend.batch.fake.FakeSummaryRepository;
import com.vulntrend.batch.fake.FakeSyncStateRepository;
import com.vulntrend.batch.fake.FakeTrendScoreRepository;
import com.vulntrend.batch.global.config.SummaryProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("하루 한 번 실행")
@ExtendWith(OutputCaptureExtension.class)
class BatchRunnerTest {

    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FakeTrendScoreRepository scores = new FakeTrendScoreRepository(3);
    private final FakeDailyStatRepository stats = new FakeDailyStatRepository();

    @Test
    @DisplayName("수집이 실패한 소스가 있으면 점수 매기기와 게시로 넘어가지 않는다")
    void stopsAfterCollectionFailure(CapturedOutput output) {
        // given: NVD 는 성공, KEV 는 실패
        BatchRunner runner = runnerWith(
                FakeCollector.loading(SourceName.NVD, 3), FakeCollector.failing(SourceName.KEV));

        // when
        runner.run();

        // then
        assertThat(output).contains("scoring and publishing are skipped");
        assertThat(output).contains("KEV");
        assertThat(output).doesNotContain("Collection finished");
    }

    @Test
    @DisplayName("모든 소스를 다 가져왔으면 다음 단계로 넘어간다")
    void continuesAfterCollection(CapturedOutput output) {
        BatchRunner runner = runnerWith(FakeCollector.loading(SourceName.NVD, 3));

        runner.run();

        assertThat(output).contains("Collection finished");
        assertThat(output).doesNotContain("scoring and publishing are skipped");
    }

    @Test
    @DisplayName("지난 기간을 한 번 가져오는 실행은 NVD 만 가져오고 점수 매기기와 게시로 넘어가지 않는다")
    void backfillRunsNvdOnly(CapturedOutput output) {
        FakeCollector nvd = FakeCollector.loading(SourceName.NVD, 3);
        FakeCollector kev = FakeCollector.loading(SourceName.KEV, 1);

        runnerWith(nvd, kev).run("--backfill-days=365");

        assertThat(nvd.receivedWindows()).hasSize(1);
        assertThat(kev.receivedWindows()).isEmpty();
        assertThat(output).contains("Backfill of NVD for the last 365 days loaded 3 records");
        assertThat(output).doesNotContain("Collection finished");
    }

    @Test
    @DisplayName("지난 기간 가져오기가 실패하면 실패를 남기고 끝낸다")
    void backfillFailure(CapturedOutput output) {
        runnerWith(FakeCollector.failing(SourceName.NVD)).run("--backfill-days=30");

        assertThat(output).contains("Backfill of NVD for the last 30 days failed");
    }

    @Test
    @DisplayName("수집이 모두 끝나면 대표 제품을 고른 뒤 점수 단계로 넘어간다")
    void assignsPrimaryProductsAfterCollection(CapturedOutput output) {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3), FakeCollector.loading(SourceName.KEV, 1)).run();

        assertThat(output).contains("Primary product changed");
        assertThat(output.toString().indexOf("Primary product changed"))
                .isLessThan(output.toString().indexOf("Trend score recalculated"));
    }

    @Test
    @DisplayName("수집이 모두 끝나면 이번 실행 시각을 기준으로 점수를 다시 매긴다")
    void rescoresAfterCollection() {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run();

        assertThat(scores.rescoredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("수집이 실패한 실행에서는 점수를 다시 매기지 않는다")
    void noRescoreAfterFailure() {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3), FakeCollector.failing(SourceName.KEV)).run();

        assertThat(scores.rescoredAt()).isNull();
    }

    @Test
    @DisplayName("점수를 다시 매긴 뒤 최근 일별 통계를 다시 센다")
    void recountsStatsAfterScoring(CapturedOutput output) {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run();

        assertThat(stats.recalculated()).isNotNull();
        assertThat(output.toString().indexOf("Trend score recalculated"))
                .isLessThan(output.toString().indexOf("Daily statistics recalculated"));
    }

    @Test
    @DisplayName("일별 통계를 다시 센 뒤 한국어 요약 단계로 넘어간다")
    void summarizesAfterStats(CapturedOutput output) {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run();

        assertThat(output.toString().indexOf("Daily statistics recalculated"))
                .isLessThan(output.toString().indexOf("Korean summary is skipped"));
    }

    @Test
    @DisplayName("수집이 실패한 실행에서는 일별 통계를 다시 세지 않는다")
    void noStatsAfterFailure() {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3), FakeCollector.failing(SourceName.KEV)).run();

        assertThat(stats.recalculated()).isNull();
    }

    @Test
    @DisplayName("지난 기간을 한 번 가져오는 실행에서는 일별 통계를 다시 세지 않는다")
    void noStatsInBackfill() {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run("--backfill-days=30");

        assertThat(stats.recalculated()).isNull();
    }

    @Test
    @DisplayName("수집이 실패한 실행에서는 한국어 요약 단계로 넘어가지 않는다")
    void noSummaryAfterFailure(CapturedOutput output) {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3), FakeCollector.failing(SourceName.KEV)).run();

        assertThat(output).doesNotContain("Korean summary");
    }

    @Test
    @DisplayName("지난 기간을 한 번 가져오는 실행에서는 점수를 다시 매기지 않는다")
    void noRescoreInBackfill() {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run("--backfill-days=30");

        assertThat(scores.rescoredAt()).isNull();
    }

    @Test
    @DisplayName("수집이 실패한 실행에서는 대표 제품도 고르지 않는다")
    void noAssignmentAfterFailure(CapturedOutput output) {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3), FakeCollector.failing(SourceName.EPSS)).run();

        assertThat(output).doesNotContain("Primary product changed");
        assertThat(output).doesNotContain("Trend score recalculated");
    }

    @Test
    @DisplayName("지난 기간을 한 번 가져오는 실행에서는 대표 제품을 고르지 않는다")
    void noAssignmentInBackfill(CapturedOutput output) {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run("--backfill-days=30");

        assertThat(output).doesNotContain("Primary product changed");
    }

    private BatchRunner runnerWith(FakeCollector... collectors) {
        ProductAssignService assignment = new ProductAssignService(
                new FakeProductAliasCatalog(ProductAliases.of(List.of())),
                new FakeProductAssignmentRepository(List.of(), List.of()));
        return new BatchRunner(new CollectService(List.of(collectors), new FakeSyncStateRepository(), clock),
                assignment, new ScoreService(scores, clock), new DailyStatService(stats, clock),
                new SummaryService(new SummaryProperties(20, "", ""), Optional.empty(),
                        new FakeSummaryRepository(List.of(), 0), clock));
    }
}
