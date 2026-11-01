package com.vulntrend.batch.domain.stat;

import java.time.LocalDate;
import java.util.Optional;

/** KPI 집계 저장소. */
public interface KpiRepository {

    /** 창 안에 새로 들어온 수 */
    NewArrivals newArrivals(KpiWindow window);

    /** 등재일이 first~last(둘 다 포함) 안인 KEV 수. 철회 건은 세지 않는다 */
    int kevAddedBetween(LocalDate first, LocalDate last);

    /** 지금 전체 중 고위험 수 */
    int highRiskNow();

    /** 그 날(한국 달력일) 일별 통계에 남은 고위험 수. 그 날 실행이 없었으면 비어 있다 */
    Optional<Integer> highRiskRecordedOn(LocalDate day);
}
