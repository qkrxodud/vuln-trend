package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.stat.DailyStat;
import com.vulntrend.batch.domain.stat.DailyStatRepository;
import com.vulntrend.batch.domain.stat.StatDays;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 일별 통계 저장소 흉내. 다시 센 날들을 기억하고, 넣어 둔 날치 통계를 날짜 순서로 돌려준다 */
public class FakeDailyStatRepository implements DailyStatRepository {

    private final Map<LocalDate, DailyStat> stats = new TreeMap<>();
    private StatDays recalculated;

    public FakeDailyStatRepository with(DailyStat stat) {
        stats.put(stat.getStatDate(), stat);
        return this;
    }

    @Override
    public int recalculate(StatDays days) {
        this.recalculated = days;
        return days.count();
    }

    @Override
    public List<DailyStat> findBetween(LocalDate first, LocalDate last) {
        return stats.values().stream()
                .filter(stat -> stat.getStatDate().compareTo(first) >= 0 && stat.getStatDate().compareTo(last) <= 0)
                .toList();
    }

    public StatDays recalculated() {
        return recalculated;
    }
}
