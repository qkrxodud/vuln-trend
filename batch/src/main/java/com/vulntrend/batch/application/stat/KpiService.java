package com.vulntrend.batch.application.stat;

import com.vulntrend.batch.domain.stat.KpiCounts;
import com.vulntrend.batch.domain.stat.KpiRepository;
import com.vulntrend.batch.domain.stat.KpiSummary;
import com.vulntrend.batch.domain.stat.KpiWindow;
import com.vulntrend.batch.domain.stat.NewArrivals;
import com.vulntrend.batch.domain.stat.StatDays;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * KPI 집계(기획서 8장 KPI 표, WBS 3.4). meta.json 을 쓸 때(4.2) 이번 실행 시각으로 부른다.
 * - 신규·KEV 신규·Critical: 지금 창과 어제 창(24시간 롤링)에서 센다
 * - 고위험: 지금은 전체를 새로 세고, 어제는 어제 한국 달력일의 일별 통계에 남은 수(없으면 비움, 확정 2026-10-31 (v))
 */
@Service
@RequiredArgsConstructor
public class KpiService {

    private final KpiRepository repository;

    public KpiSummary summarize(Instant now) {
        NewArrivals todayArrivals = repository.newArrivals(KpiWindow.today(now));
        NewArrivals yesterdayArrivals = repository.newArrivals(KpiWindow.yesterday(now));
        LocalDate yesterday = StatDays.recent(now).getToday().minusDays(1);
        KpiCounts today = KpiCounts.of(todayArrivals, repository.highRiskNow());
        KpiCounts before = repository.highRiskRecordedOn(yesterday)
                .map(count -> KpiCounts.of(yesterdayArrivals, count))
                .orElseGet(() -> KpiCounts.withoutHighRisk(yesterdayArrivals));
        return KpiSummary.of(today, before);
    }
}
