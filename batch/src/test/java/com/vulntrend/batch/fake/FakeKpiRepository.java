package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.stat.KpiRepository;
import com.vulntrend.batch.domain.stat.KpiWindow;
import com.vulntrend.batch.domain.stat.NewArrivals;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** KPI 저장소 흉내. 창 시작 시각마다 정해 둔 수와 날마다 남긴 고위험 수를 돌려준다 */
public class FakeKpiRepository implements KpiRepository {

    private final Map<Instant, NewArrivals> arrivals = new HashMap<>();
    private final Map<LocalDate, Integer> recorded = new HashMap<>();
    private int highRiskNow;

    public FakeKpiRepository arrivals(Instant from, int newCount, int kevNewCount, int criticalCount) {
        arrivals.put(from, NewArrivals.of(newCount, kevNewCount, criticalCount));
        return this;
    }

    public FakeKpiRepository highRiskNow(int count) {
        this.highRiskNow = count;
        return this;
    }

    public FakeKpiRepository highRiskRecorded(LocalDate day, int count) {
        recorded.put(day, count);
        return this;
    }

    @Override
    public NewArrivals newArrivals(KpiWindow window) {
        return arrivals.getOrDefault(window.getFrom(), NewArrivals.of(0, 0, 0));
    }

    @Override
    public int highRiskNow() {
        return highRiskNow;
    }

    @Override
    public Optional<Integer> highRiskRecordedOn(LocalDate day) {
        return Optional.ofNullable(recorded.get(day));
    }
}
