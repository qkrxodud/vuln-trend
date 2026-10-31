package com.vulntrend.batch.domain.stat;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;

/**
 * KPI 4개(meta.json today·yesterday 의 new·kev_new·critical·high_risk, G1). 고위험은 EPSS 를 날마다 덮어써서
 * 지난 창의 값을 다시 셀 수 없다 — 어제 값은 일별 통계에 남은 수이고, 없으면 비어 있다(확정 2026-10-31, (v)).
 */
public class KpiCounts {

    private final NewArrivals arrivals;
    private final Integer highRiskCount;

    @Builder(access = AccessLevel.PRIVATE)
    private KpiCounts(NewArrivals arrivals, Integer highRiskCount) {
        this.arrivals = arrivals;
        this.highRiskCount = highRiskCount;
    }

    public static KpiCounts of(NewArrivals arrivals, int highRiskCount) {
        return KpiCounts.builder().arrivals(arrivals).highRiskCount(highRiskCount).build();
    }

    public static KpiCounts withoutHighRisk(NewArrivals arrivals) {
        return KpiCounts.builder().arrivals(arrivals).build();
    }

    public int getNewCount() {
        return arrivals.getNewCount();
    }

    public int getKevNewCount() {
        return arrivals.getKevNewCount();
    }

    public int getCriticalCount() {
        return arrivals.getCriticalCount();
    }

    public Optional<Integer> getHighRiskCount() {
        return Optional.ofNullable(highRiskCount);
    }
}
