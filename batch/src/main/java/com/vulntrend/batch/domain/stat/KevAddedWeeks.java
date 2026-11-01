package com.vulntrend.batch.domain.stat;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 최근 7일·그 앞 7일에 KEV 에 등재된 수(meta.json kev_added_7d·kev_added_prev_7d). 날짜 경계는 KpiService */
@Getter
public class KevAddedWeeks {

    private final int thisWeek;
    private final int previousWeek;

    @Builder(access = AccessLevel.PRIVATE)
    private KevAddedWeeks(int thisWeek, int previousWeek) {
        this.thisWeek = thisWeek;
        this.previousWeek = previousWeek;
    }

    public static KevAddedWeeks of(int thisWeek, int previousWeek) {
        return KevAddedWeeks.builder().thisWeek(thisWeek).previousWeek(previousWeek).build();
    }
}
