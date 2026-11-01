package com.vulntrend.batch.application.stat;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.stat.KevAddedWeeks;
import com.vulntrend.batch.domain.stat.KpiSummary;
import com.vulntrend.batch.fake.FakeKpiRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("KPI 집계")
class KpiServiceTest {

    /** 한국 시각 2026-10-31 09:00 실행 */
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Test
    @DisplayName("지금 창과 어제 창에서 신규·KEV 신규·Critical 을 센다")
    void arrivalsOfBothWindows() {
        FakeKpiRepository repository = new FakeKpiRepository()
                .arrivals(Instant.parse("2026-10-30T00:00:00Z"), 134, 1, 10)
                .arrivals(Instant.parse("2026-10-29T00:00:00Z"), 135, 1, 9);

        KpiSummary summary = new KpiService(repository).summarize(NOW);

        assertThat(summary.getToday().getNewCount()).isEqualTo(134);
        assertThat(summary.getToday().getKevNewCount()).isEqualTo(1);
        assertThat(summary.getToday().getCriticalCount()).isEqualTo(10);
        assertThat(summary.getYesterday().getNewCount()).isEqualTo(135);
        assertThat(summary.getYesterday().getCriticalCount()).isEqualTo(9);
    }

    @Test
    @DisplayName("지금 고위험은 전체를 새로 세고 어제 고위험은 어제 한국 달력일 통계에 남은 수를 쓴다")
    void highRiskNowAndRecorded() {
        FakeKpiRepository repository = new FakeKpiRepository().highRiskNow(318)
                .highRiskRecorded(LocalDate.parse("2026-10-30"), 313);

        KpiSummary summary = new KpiService(repository).summarize(NOW);

        assertThat(summary.getToday().getHighRiskCount()).contains(318);
        assertThat(summary.getYesterday().getHighRiskCount()).contains(313);
    }

    @Test
    @DisplayName("어제 실행이 없어 어제 고위험을 모르면 비워 둔다")
    void unknownYesterdayHighRisk() {
        FakeKpiRepository repository = new FakeKpiRepository().highRiskNow(318);

        KpiSummary summary = new KpiService(repository).summarize(NOW);

        assertThat(summary.getYesterday().getHighRiskCount()).isEmpty();
    }

    @Nested
    @DisplayName("최근 두 주 KEV 등재 수는")
    class KevAddedWeeksCount {

        private final FakeKpiRepository repository = new FakeKpiRepository()
                .kevAddedOn(LocalDate.parse("2026-10-31"), 1)
                .kevAddedOn(LocalDate.parse("2026-10-25"), 2)
                .kevAddedOn(LocalDate.parse("2026-10-24"), 4)
                .kevAddedOn(LocalDate.parse("2026-10-18"), 8)
                .kevAddedOn(LocalDate.parse("2026-10-17"), 16);

        @Test
        @DisplayName("최근 7일은 오늘(한국 달력일)을 넣은 7개 날짜, 그 전 7일은 그 앞 7개 날짜에 등재된 수다")
        void sevenDaysIncludingToday() {
            KevAddedWeeks weeks = new KpiService(repository).kevAddedWeeks(NOW);

            assertThat(weeks.getThisWeek()).isEqualTo(1 + 2);
            assertThat(weeks.getPreviousWeek()).isEqualTo(4 + 8);
        }

        @Test
        @DisplayName("한국 자정을 넘긴 직후 실행이면 한국 날짜로 오늘을 정한다")
        void koreanCalendarDay() {
            KevAddedWeeks weeks = new KpiService(repository).kevAddedWeeks(Instant.parse("2026-10-31T15:30:00Z"));

            assertThat(weeks.getThisWeek()).isEqualTo(1);
            assertThat(weeks.getPreviousWeek()).isEqualTo(2 + 4);
        }
    }
}
