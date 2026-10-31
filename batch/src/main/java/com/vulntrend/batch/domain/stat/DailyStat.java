package com.vulntrend.batch.domain.stat;

import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 일별 통계 한 날치(daily_stat 의 한국 달력일 하루, WBS 4.2 stats.json). 심각도 다섯 갈래의 합이 신규 수다 */
@Getter
public class DailyStat {

    private final LocalDate statDate;
    private final int newCount;
    private final int criticalCount;
    private final int highCount;
    private final int mediumCount;
    private final int lowCount;
    private final int unratedCount;

    @Builder(access = AccessLevel.PRIVATE)
    private DailyStat(LocalDate statDate, int newCount, int criticalCount, int highCount, int mediumCount,
            int lowCount, int unratedCount) {
        this.statDate = statDate;
        this.newCount = newCount;
        this.criticalCount = criticalCount;
        this.highCount = highCount;
        this.mediumCount = mediumCount;
        this.lowCount = lowCount;
        this.unratedCount = unratedCount;
    }

    public static DailyStat of(LocalDate statDate, int newCount, int criticalCount, int highCount, int mediumCount,
            int lowCount, int unratedCount) {
        return DailyStat.builder().statDate(statDate).newCount(newCount).criticalCount(criticalCount)
                .highCount(highCount).mediumCount(mediumCount).lowCount(lowCount).unratedCount(unratedCount).build();
    }

    /** 통계가 없는 날(아직 다시 세지 않은 날)은 모두 0 으로 본다 */
    public static DailyStat empty(LocalDate statDate) {
        return of(statDate, 0, 0, 0, 0, 0, 0);
    }
}
