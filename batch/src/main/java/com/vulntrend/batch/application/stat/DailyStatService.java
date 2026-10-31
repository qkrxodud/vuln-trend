package com.vulntrend.batch.application.stat;

import com.vulntrend.batch.domain.stat.DailyStatRepository;
import com.vulntrend.batch.domain.stat.StatDays;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 일별 통계 다시 세기(기획서 6장 6번, WBS 3.3). 점수 다음에 러너가 부른다.
 * "오늘"은 SQL 의 현재 시각이 아니라 Clock 에서 얻은 이번 실행 시각의 한국 달력일이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyStatService {

    private final DailyStatRepository repository;
    private final Clock clock;

    public int recalculateRecent() {
        StatDays days = StatDays.recent(clock.instant());
        int written = repository.recalculate(days);
        log.info("Daily statistics recalculated for {} days from {} to {}.", written, days.getFirst(),
                days.getToday());
        return written;
    }
}
