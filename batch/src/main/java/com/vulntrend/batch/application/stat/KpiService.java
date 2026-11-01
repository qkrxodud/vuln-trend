package com.vulntrend.batch.application.stat;

import com.vulntrend.batch.domain.stat.KevAddedWeeks;
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
 * - 최근 두 주 KEV 등재 수(meta kev_added_7d·kev_added_prev_7d, v6 화면용 사용자 승인 2026-11-01): 전체 KEV 중 등재일이
 *   오늘(실행 시각의 한국 달력일)을 넣은 7개 날짜 [오늘-6, 오늘] 안인 것과, 그 앞 7개 날짜 [오늘-13, 오늘-7] 안인 것.
 *   등재일은 CISA 가 적은 날짜 그대로 비교한다(시간대 바꿈 없음)
 */
@Service
@RequiredArgsConstructor
public class KpiService {

    private static final int WEEK = 7;

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

    /** 최근 7일(오늘 포함 한국 달력일 7개)과 그 앞 7일에 등재된 KEV 수 */
    public KevAddedWeeks kevAddedWeeks(Instant now) {
        LocalDate today = StatDays.recent(now).getToday();
        LocalDate weekStart = today.minusDays(WEEK - 1);
        return KevAddedWeeks.of(repository.kevAddedBetween(weekStart, today),
                repository.kevAddedBetween(weekStart.minusDays(WEEK), weekStart.minusDays(1)));
    }
}
