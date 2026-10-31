package com.vulntrend.batch.application.stat;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.stat.KpiSummary;
import com.vulntrend.batch.fake.FakeKpiRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
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
}
