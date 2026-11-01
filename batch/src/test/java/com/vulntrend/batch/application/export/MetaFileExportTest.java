package com.vulntrend.batch.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.dto.MetaJson;
import com.vulntrend.batch.application.stat.KpiService;
import com.vulntrend.batch.fake.FakeKpiRepository;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("meta.json")
class MetaFileExportTest {

    /** 한국 시각 2026-10-31 09:00:07.5 실행 */
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:07.500Z");

    private final FakeSiteDataWriter writer = new FakeSiteDataWriter();

    @Test
    @DisplayName("갱신 시각은 이번 실행 시각을 한국 시각 초 단위로 적고 판 이름은 한국 시각 분까지 적는다")
    void updatedAtAndVersion() {
        MetaJson meta = export(new FakeKpiRepository());

        assertThat(meta.updatedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 31, 9, 0, 7, 0, ZoneOffset.ofHours(9)));
        assertThat(meta.version()).isEqualTo("20261031-0900");
    }

    @Test
    @DisplayName("KPI 4개를 지금 창과 어제 창으로 싣는다")
    void kpiCounts() {
        MetaJson meta = export(new FakeKpiRepository()
                .arrivals(Instant.parse("2026-10-30T00:00:07.500Z"), 134, 1, 10)
                .arrivals(Instant.parse("2026-10-29T00:00:07.500Z"), 135, 2, 9)
                .highRiskNow(318)
                .highRiskRecorded(LocalDate.parse("2026-10-30"), 313));

        assertThat(meta.today().newCount()).isEqualTo(134);
        assertThat(meta.today().kevNew()).isEqualTo(1);
        assertThat(meta.today().critical()).isEqualTo(10);
        assertThat(meta.today().highRisk()).isEqualTo(318);
        assertThat(meta.yesterday().newCount()).isEqualTo(135);
        assertThat(meta.yesterday().kevNew()).isEqualTo(2);
        assertThat(meta.yesterday().critical()).isEqualTo(9);
        assertThat(meta.yesterday().highRisk()).isEqualTo(313);
    }

    @Test
    @DisplayName("전체 KEV 중 최근 7일과 그 전 7일에 등재된 수를 싣는다")
    void kevAddedTwoWeeks() {
        MetaJson meta = export(new FakeKpiRepository()
                .kevAddedOn(LocalDate.parse("2026-10-31"), 3)
                .kevAddedOn(LocalDate.parse("2026-10-24"), 5));

        assertThat(meta.kevAdded7d()).isEqualTo(3);
        assertThat(meta.kevAddedPrev7d()).isEqualTo(5);
    }

    @Test
    @DisplayName("어제 고위험을 모르면 비워서 화면이 증감을 숨기게 한다")
    void unknownYesterdayHighRisk() {
        MetaJson meta = export(new FakeKpiRepository().highRiskNow(318));

        assertThat(meta.today().highRisk()).isEqualTo(318);
        assertThat(meta.yesterday().highRisk()).isNull();
    }

    private MetaJson export(FakeKpiRepository repository) {
        new MetaFileExport(new KpiService(repository), writer).write(NOW);
        return writer.document("meta.json", MetaJson.class).orElseThrow();
    }
}
