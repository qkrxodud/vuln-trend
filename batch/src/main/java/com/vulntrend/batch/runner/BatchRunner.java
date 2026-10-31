package com.vulntrend.batch.runner;

import com.vulntrend.batch.application.collect.CollectOutcome;
import com.vulntrend.batch.application.collect.CollectService;
import com.vulntrend.batch.application.product.ProductAssignService;
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
 * 하루 한 번 실행(WBS 2.4): 수집(1~4) → 하나라도 실패하면 여기서 멈춘다(early return, 반쪽 JSON 을 게시하지 않는다)
 *   → 대표 제품 고르기(백엔드 B 2.3, KEV·NVD 원본을 함께 보므로 수집 뒤 한 번) → 점수(5) 이후.
 * 점수(5)·통계(6)·요약(7)·출력(8)·게시(9)는 scoreAndPublish() 자리에 각 Phase 에서 이어 붙인다.
 * 소스별 진행 기록은 CollectService 가 따로 남긴다 — 한 소스가 실패해도 다른 소스 커서는 전진한다.
 * --backfill-days=N 이면 지난 N일 NVD 만 한 번 가져오고 끝낸다(WBS 1.5).
 * batch.runner.enabled=false 면 등록하지 않는다 — 테스트 프로필이 실제 NVD 를 부르지 않게 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "batch.runner", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class BatchRunner implements CommandLineRunner {

    private final CollectService collect;
    private final ProductAssignService productAssignment;

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
        productAssignment.assignAll();
        scoreAndPublish();
    }

    // 연결 지점: Phase 3(점수·통계)·6(요약)·4(출력·게시)가 여기에 순서대로 들어온다
    private void scoreAndPublish() {
        log.info("Scoring step is not wired yet; this run ends after collection and product assignment.");
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
