package com.vulntrend.batch.runner;

import com.vulntrend.batch.application.collect.CollectOutcome;
import com.vulntrend.batch.application.collect.CollectService;
import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.SourceName;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 하루 한 번 실행의 진입점. 단계 순서와 실패 규칙(기획서 6장)을 적용한다.
 * 뼈대(WBS 0.3): 수집 → 실패 시 early return 까지. 점수(5)·통계(6)·요약(7)·출력(8)·게시(9)는 각 Phase 에서 이어 붙인다.
 * --backfill-days=N 이면 지난 N일 NVD 만 한 번 가져오고 끝낸다(WBS 1.5).
 * batch.runner.enabled=false 면 등록하지 않는다 — 테스트 프로필이 실제 NVD 를 부르지 않게 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "batch.runner", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class BatchRunner implements CommandLineRunner {

    private final CollectService collect;

    @Override
    public void run(String... args) {
        RunMode mode = RunMode.from(args);
        if (mode.isBackfill()) {
            backfill(mode.getBackfillSpan());
            return;
        }
        CollectOutcome collected = collect.collectAll();
        if (collected.hasFailure()) {
            log.warn("Collection failed for {}; scoring and publishing are skipped this run.", collected.failedSources());
            return;
        }
        log.info("Collection finished for {} sources.", collected.getResults().size());
    }

    // 1회성 실행: NVD 만 가져오고 점수·게시 단계로 넘어가지 않는다. 다음 하루 한 번 실행이 이어서 점수를 매긴다
    private void backfill(Duration span) {
        CollectResult result = collect.backfill(SourceName.NVD, span);
        if (result.isFailed()) {
            log.warn("Backfill of NVD for the last {} days failed; run it again to retry the same period.", span.toDays());
            return;
        }
        log.info("Backfill of NVD for the last {} days loaded {} records; scoring waits for the next daily run.",
                span.toDays(), result.getLoadedCount());
    }
}
