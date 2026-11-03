package com.vulntrend.batch.application.score;

import com.vulntrend.batch.domain.enrichment.EpssRepository;
import com.vulntrend.batch.domain.score.DecayBase;
import com.vulntrend.batch.domain.score.TrendScoreRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 점수 다시 매기기(기획서 6장 5단계, WBS 3.1). 수집이 모두 성공한 실행에서만 러너가 부른다(실패 규칙, 2.4).
 * 감쇠 기준 "지금"은 실행 시각이 아니라 하루 한 번 바뀌는 감쇠 기준 시각(DecayBase, S4)이다 — 같은 날 실행끼리 같은 점수가 나와
 * 주목 순위와 사이트 파일이 2시간마다 흔들리지 않는다. 사이트 파일을 쓰는 쪽도 decayBase() 로 같은 시각을 받는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    private final TrendScoreRepository repository;
    private final EpssRepository epss;
    private final Clock clock;

    public int rescoreAll() {
        Optional<LocalDate> epssDate = epss.appliedScoreDate();
        DecayBase base = DecayBase.of(epssDate, clock.instant());
        if (base.isFallback()) {
            log.warn("Decay base fell back to Korean midnight {} because the last EPSS date is {}.", base.getAt(),
                    epssDate.map(LocalDate::toString).orElse("none"));
        }
        int rescored = repository.rescoreAll(base.getAt());
        log.info("Trend score recalculated for {} vulnerabilities with decay as of {}.", rescored, base.getAt());
        return rescored;
    }

    public DecayBase decayBase() {
        return DecayBase.of(epss.appliedScoreDate(), clock.instant());
    }
}
