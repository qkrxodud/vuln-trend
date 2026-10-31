package com.vulntrend.batch.application.score;

import com.vulntrend.batch.domain.score.TrendScoreRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 점수 다시 매기기(기획서 6장 5단계, WBS 3.1). 수집이 모두 성공한 실행에서만 러너가 부른다(실패 규칙, 2.4).
 * 감쇠 기준 "지금"은 SQL 의 now() 가 아니라 Clock 에서 얻은 이번 실행 시각이다 — 같은 실행 안의 모든 계산이 같은 시각을 본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    private final TrendScoreRepository repository;
    private final Clock clock;

    public int rescoreAll() {
        Instant now = clock.instant();
        int rescored = repository.rescoreAll(now);
        log.info("Trend score recalculated for {} vulnerabilities as of {}.", rescored, now);
        return rescored;
    }
}
