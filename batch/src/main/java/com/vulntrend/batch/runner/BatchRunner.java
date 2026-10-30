package com.vulntrend.batch.runner;

import com.vulntrend.batch.application.collect.CollectOutcome;
import com.vulntrend.batch.application.collect.CollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 하루 한 번 실행의 진입점. 단계 순서와 실패 규칙(기획서 6장)을 적용한다.
 * 뼈대(WBS 0.3): 수집 → 실패 시 early return 까지. 점수(5)·통계(6)·요약(7)·출력(8)·게시(9)는 각 Phase 에서 이어 붙인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchRunner implements CommandLineRunner {

    private final CollectService collect;

    @Override
    public void run(String... args) {
        CollectOutcome collected = collect.collectAll();
        if (collected.hasFailure()) {
            log.warn("Collection failed for {}; scoring and publishing are skipped this run.", collected.failedSources());
            return;
        }
        log.info("Collection finished for {} sources.", collected.getResults().size());
    }
}
