package com.vulntrend.batch.domain.stat;

/** 일별 통계(daily_stat) 저장소. */
public interface DailyStatRepository {

    /** 그 날들의 행을 모두 다시 세어 쓰고, 쓴 날 수를 알린다 */
    int recalculate(StatDays days);
}
