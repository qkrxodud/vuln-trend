package com.vulntrend.batch.domain.stat;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 지금 창과 어제 창의 KPI(meta.json today·yesterday). 어제 대비 증감은 화면이 계산한다. */
@Getter
public class KpiSummary {

    private final KpiCounts today;
    private final KpiCounts yesterday;

    @Builder(access = AccessLevel.PRIVATE)
    private KpiSummary(KpiCounts today, KpiCounts yesterday) {
        this.today = today;
        this.yesterday = yesterday;
    }

    public static KpiSummary of(KpiCounts today, KpiCounts yesterday) {
        return KpiSummary.builder().today(today).yesterday(yesterday).build();
    }
}
