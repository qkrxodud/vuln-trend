package com.vulntrend.batch.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.vulntrend.batch.application.collect.CollectService;
import com.vulntrend.batch.application.gate.GateCheckService;
import com.vulntrend.batch.application.product.ProductAssignService;
import com.vulntrend.batch.application.score.ScoreService;
import com.vulntrend.batch.application.stat.DailyStatService;
import com.vulntrend.batch.application.summary.SummaryService;
import com.vulntrend.batch.application.vulnerability.LegacyTitleService;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.export.DataFilePath;
import com.vulntrend.batch.domain.export.WriteOutcome;
import com.vulntrend.batch.domain.product.ProductAliases;
import com.vulntrend.batch.domain.summary.SummarySource;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.fake.FakeCollector;
import com.vulntrend.batch.fake.FakeDailyStatRepository;
import com.vulntrend.batch.fake.FakeLegacyTitleRepository;
import com.vulntrend.batch.fake.FakeProductAliasCatalog;
import com.vulntrend.batch.fake.FakeProductAssignmentRepository;
import com.vulntrend.batch.fake.FakePublisher;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import com.vulntrend.batch.fake.FakeSummaryRepository;
import com.vulntrend.batch.fake.FakeSyncStateRepository;
import com.vulntrend.batch.fake.FakeTrendScoreRepository;
import com.vulntrend.batch.global.config.SummaryProperties;
import com.vulntrend.batch.global.error.exception.CollectionFailedException;
import com.vulntrend.batch.global.error.exception.GateCheckFailedException;
import com.vulntrend.batch.global.error.exception.OutputWriteException;
import com.vulntrend.batch.global.error.exception.PublishException;
import com.vulntrend.batch.support.GateChecks;
import com.vulntrend.batch.support.SiteExports;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("하루 한 번 실행")
@ExtendWith(OutputCaptureExtension.class)
class BatchRunnerTest {

    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FakeTrendScoreRepository scores = new FakeTrendScoreRepository(3);
    private final FakeDailyStatRepository stats = new FakeDailyStatRepository();
    private LegacyTitleService legacyTitles = new LegacyTitleService(new FakeLegacyTitleRepository(List.of()));
    private FakeSiteDataWriter siteData = new FakeSiteDataWriter();
    private FakePublisher publisher = FakePublisher.working();
    private GateCheckService gate = GateChecks.passing(clock, "2026-10-30T09:00", "2026-10-29T09:00",
            "2026-10-28T09:00");
    private SummaryService summary = new SummaryService(new SummaryProperties(20, "", ""), Optional.empty(),
            new FakeSummaryRepository(List.of(), 0), clock);

    @Test
    @DisplayName("Gate G 배치 점검 실행은 점검 표만 남기고 수집·점수·출력·게시는 하지 않는다")
    void gateCheckOnly(CapturedOutput output) {
        FakeCollector nvd = FakeCollector.loading(SourceName.NVD, 3);

        runnerWith(nvd).run("--gate-check");

        assertThat(output).contains("Gate G batch check", "| 항목 | 결과 | 근거 |");
        assertThat(nvd.receivedWindows()).isEmpty();
        assertThat(scores.rescoredAt()).isNull();
        assertThat(siteData.files()).isEmpty();
        assertThat(publisher.published()).isZero();
    }

    @Test
    @DisplayName("Gate G 배치 점검에 실패한 항목이 있으면 종료 코드 1 로 끝난다")
    void gateCheckFailureExitCode(CapturedOutput output) {
        gate = GateChecks.failing(clock);

        Throwable failure = catchThrowable(() -> runnerWith(FakeCollector.loading(SourceName.NVD, 3))
                .run("--gate-check"));

        assertThat(failure).isInstanceOf(GateCheckFailedException.class);
        assertThat(((ExitCodeGenerator) failure).getExitCode()).isEqualTo(1);
        assertThat(output).contains("| 항목 | 결과 | 근거 |");
    }

    @Test
    @DisplayName("수집이 실패한 실행은 종료 코드 1 로 끝나 cron·Actions 가 실패를 안다")
    void collectionFailureExitCode() {
        Throwable failure = catchThrowable(() -> runnerWith(
                FakeCollector.loading(SourceName.NVD, 3), FakeCollector.failing(SourceName.KEV)).run());

        assertThat(failure).isInstanceOf(CollectionFailedException.class).hasMessageContaining("KEV");
        assertThat(((ExitCodeGenerator) failure).getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("한국어 요약 다음에 사이트 데이터 파일을 쓰고, 바뀐 파일이 있으면 게시한다")
    void exportsThenPublishes(CapturedOutput output) {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run();

        assertThat(siteData.files()).contains("meta.json", "index.json");
        assertThat(publisher.published()).isEqualTo(1);
        assertThat(output.toString().indexOf("Korean summary is skipped"))
                .isLessThan(output.toString().indexOf("Site data exported"));
    }

    @Test
    @DisplayName("바뀐 데이터 파일이 하나도 없으면 게시하지 않는다")
    void noChangesNoPublish(CapturedOutput output) {
        // 같은 사이트 데이터 폴더·같은 게시 창구에 두 번 돌린다(수집 진행 기록은 새로 — 고정 시계에서 같은 구간을 다시 받게)
        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run();

        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run();

        assertThat(publisher.published()).isEqualTo(1);
        assertThat(output).contains("Site data is unchanged; nothing to publish");
    }

    @Test
    @DisplayName("데이터 파일을 쓰지 못하면 반쪽 데이터를 게시하지 않고 종료 코드 1 로 끝난다")
    void outputFailureStopsPublishing() {
        siteData = new FakeSiteDataWriter() {
            @Override
            public WriteOutcome write(DataFilePath path, Object document) {
                throw new OutputWriteException(path.getValue());
            }
        };

        Throwable failure = catchThrowable(() -> runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run());

        assertThat(failure).isInstanceOf(OutputWriteException.class);
        assertThat(((ExitCodeGenerator) failure).getExitCode()).isEqualTo(1);
        assertThat(publisher.published()).isZero();
    }

    @Test
    @DisplayName("게시하지 못하면 종료 코드 1 로 끝난다")
    void publishFailureExitCode() {
        publisher = FakePublisher.failing();

        Throwable failure = catchThrowable(() -> runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run());

        assertThat(failure).isInstanceOf(PublishException.class);
        assertThat(((ExitCodeGenerator) failure).getExitCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("한국어 요약 단계가 실패해도 데이터 파일을 쓰고 게시한다")
    void summaryFailureIsNotFatal() {
        summary = new SummaryService(new SummaryProperties(20, "gpt-6-luna", "key"),
                Optional.of(source -> { throw new IllegalStateException("down"); }),
                new FakeSummaryRepository(List.of(SummarySource.of(CveId.from("CVE-2026-0001"), "A flaw.", List.of(),
                        null)), 0), clock);

        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run();

        assertThat(publisher.published()).isEqualTo(1);
    }

    @Test
    @DisplayName("수집이 실패한 실행은 데이터 파일을 쓰지도 게시하지도 않는다")
    void noExportAfterCollectionFailure() {
        catchThrowable(() -> runnerWith(FakeCollector.loading(SourceName.NVD, 3),
                FakeCollector.failing(SourceName.EPSS)).run());

        assertThat(siteData.files()).isEmpty();
        assertThat(publisher.published()).isZero();
    }

    @Test
    @DisplayName("지난 기간을 한 번 가져오는 실행은 데이터 파일을 쓰지도 게시하지도 않는다")
    void noExportInBackfill() {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run("--backfill-days=30");

        assertThat(siteData.files()).isEmpty();
        assertThat(publisher.published()).isZero();
    }

    @Test
    @DisplayName("지난 기간 가져오기가 실패한 실행도 종료 코드 1 로 끝난다")
    void backfillFailureExitCode() {
        Throwable failure = catchThrowable(() -> runnerWith(FakeCollector.failing(SourceName.NVD))
                .run("--backfill-days=30"));

        assertThat(failure).isInstanceOf(CollectionFailedException.class);
    }

    @Test
    @DisplayName("수집이 실패한 소스가 있으면 점수 매기기와 게시로 넘어가지 않는다")
    void stopsAfterCollectionFailure(CapturedOutput output) {
        // given: NVD 는 성공, KEV 는 실패
        BatchRunner runner = runnerWith(
                FakeCollector.loading(SourceName.NVD, 3), FakeCollector.failing(SourceName.KEV));

        // when
        catchThrowable(runner::run);

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
        catchThrowable(() -> runnerWith(FakeCollector.failing(SourceName.NVD)).run("--backfill-days=30"));

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
        catchThrowable(() -> runnerWith(FakeCollector.loading(SourceName.NVD, 3), FakeCollector.failing(SourceName.KEV)).run());

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
    @DisplayName("일별 통계 다음, 한국어 요약 앞에서 옛 규칙으로 잘린 제목을 다시 계산한다")
    void renewsLegacyTitlesBeforeSummary(CapturedOutput output) {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run();

        String log = output.toString();
        assertThat(log.indexOf("Daily statistics recalculated")).isLessThan(log.indexOf("Legacy cut titles renewed"));
        assertThat(log.indexOf("Legacy cut titles renewed")).isLessThan(log.indexOf("Korean summary is skipped"));
    }

    @Test
    @DisplayName("지난 기간을 한 번 가져오는 실행과 수집이 실패한 실행에서는 제목을 다시 계산하지 않는다")
    void noRenewalInBackfillOrFailure(CapturedOutput output) {
        runnerWith(FakeCollector.loading(SourceName.NVD, 3)).run("--backfill-days=30");
        catchThrowable(() -> runnerWith(FakeCollector.loading(SourceName.NVD, 3),
                FakeCollector.failing(SourceName.KEV)).run());

        assertThat(output).doesNotContain("Legacy cut titles renewed");
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
        catchThrowable(() -> runnerWith(FakeCollector.loading(SourceName.NVD, 3), FakeCollector.failing(SourceName.KEV)).run());

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
        catchThrowable(() -> runnerWith(FakeCollector.loading(SourceName.NVD, 3), FakeCollector.failing(SourceName.KEV)).run());

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
        catchThrowable(() -> runnerWith(FakeCollector.loading(SourceName.NVD, 3), FakeCollector.failing(SourceName.EPSS)).run());

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
                assignment, new ScoreService(scores, clock), new DailyStatService(stats, clock), legacyTitles, summary,
                SiteExports.writingTo(siteData, clock), publisher, gate);
    }
}
