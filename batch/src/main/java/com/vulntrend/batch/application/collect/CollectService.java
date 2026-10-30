package com.vulntrend.batch.application.collect;

import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.Collector;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStateRepository;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import com.vulntrend.batch.global.error.VulnTrendException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 수집 순회(기획서 6장 1~4단계). 등록된 수집기를 하나씩 돌리고 소스마다 진행 기록을 따로 남긴다.
 * 한 소스의 실패(우리 예외)는 그 소스만 실패로 기록하고 다음 소스로 넘어간다 — 다른 소스 커서를 막지 않는다(WBS 2.4).
 * 우리 예외가 아닌 것(프로그램 오류·DB 장애)은 잡지 않는다 — 실행 전체가 멈추고 아무것도 게시되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectService {

    private final List<Collector> collectors;
    private final SyncStateRepository syncStates;
    private final Clock clock;

    public CollectOutcome collectAll() {
        Instant now = clock.instant();
        List<CollectResult> results = new ArrayList<>();
        for (Collector collector : collectors) {
            results.add(collectOne(collector, now));
        }
        return CollectOutcome.of(results);
    }

    private CollectResult collectOne(Collector collector, Instant now) {
        SyncState state = syncStates.findBySource(collector.source());
        try {
            SyncWindow window = state.windowUntil(now);
            CollectResult result = collector.collect(window);
            syncStates.save(state.succeeded(window, now));
            log.info("Source {} loaded {} records for window {}.", collector.source(), result.getLoadedCount(), window);
            return result;
        } catch (VulnTrendException failed) {
            syncStates.save(state.failed(now));
            log.warn("Source {} did not complete this run: {}", collector.source(), failed.getMessage());
            return CollectResult.failed(collector.source());
        }
    }
}
