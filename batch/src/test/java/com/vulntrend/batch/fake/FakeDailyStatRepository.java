package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.stat.DailyStatRepository;
import com.vulntrend.batch.domain.stat.StatDays;

/** 일별 통계 저장소 흉내. 다시 센 날들을 기억하고 그 날 수를 돌려준다 */
public class FakeDailyStatRepository implements DailyStatRepository {

    private StatDays recalculated;

    @Override
    public int recalculate(StatDays days) {
        this.recalculated = days;
        return days.count();
    }

    public StatDays recalculated() {
        return recalculated;
    }
}
