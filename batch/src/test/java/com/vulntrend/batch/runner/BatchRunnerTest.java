package com.vulntrend.batch.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.collect.CollectService;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.fake.FakeCollector;
import com.vulntrend.batch.fake.FakeSyncStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("하루 한 번 실행")
@ExtendWith(OutputCaptureExtension.class)
class BatchRunnerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T00:00:00Z"), ZoneOffset.UTC);

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
        assertThat(output).doesNotContain("skipped");
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

    private BatchRunner runnerWith(FakeCollector... collectors) {
        return new BatchRunner(new CollectService(List.of(collectors), new FakeSyncStateRepository(), clock));
    }
}
