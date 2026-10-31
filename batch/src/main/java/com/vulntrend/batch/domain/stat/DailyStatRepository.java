package com.vulntrend.batch.domain.stat;

import java.time.LocalDate;
import java.util.List;

/** 일별 통계(daily_stat) 저장소. */
public interface DailyStatRepository {

    /** 그 날들의 행을 모두 다시 세어 쓰고, 쓴 날 수를 알린다 */
    int recalculate(StatDays days);

    /** 그 사이(양끝 포함) 날들의 통계. 날짜 순서, 통계가 없는 날은 빠진다 */
    List<DailyStat> findBetween(LocalDate first, LocalDate last);
}
