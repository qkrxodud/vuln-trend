package com.vulntrend.batch.application.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.application.export.dto.DailyCountJson;
import com.vulntrend.batch.application.export.dto.StatsJson;
import com.vulntrend.batch.domain.stat.DailyStat;
import com.vulntrend.batch.fake.FakeDailyStatRepository;
import com.vulntrend.batch.fake.FakeSiteDataWriter;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("stats.json")
class StatsFileExportTest {

    /** 한국 시각 2026-10-31 09:00 실행. 14일은 10-18 ~ 10-31, 7일은 10-25 ~ 10-31 */
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    private final FakeSiteDataWriter writer = new FakeSiteDataWriter();

    @Test
    @DisplayName("최근 14일을 오래된 날부터 정확히 14칸 싣고 통계가 없는 날은 0 으로 채운다")
    void fourteenDaysOldestFirst() {
        StatsJson stats = export(new FakeDailyStatRepository()
                .with(stat("2026-10-17", 99, 9))
                .with(stat("2026-10-18", 138, 9))
                .with(stat("2026-10-31", 12, 1)));

        assertThat(stats.daily()).hasSize(14);
        assertThat(stats.daily().get(0)).isEqualTo(new DailyCountJson(LocalDate.parse("2026-10-18"), 138, 9));
        assertThat(stats.daily().get(1)).isEqualTo(new DailyCountJson(LocalDate.parse("2026-10-19"), 0, 0));
        assertThat(stats.daily().get(13)).isEqualTo(new DailyCountJson(LocalDate.parse("2026-10-31"), 12, 1));
    }

    @Test
    @DisplayName("심각도 분포는 최근 7개 달력일 신규를 미평가까지 다섯 갈래로 더한다")
    void severityOfSevenDays() {
        StatsJson stats = export(new FakeDailyStatRepository()
                .with(DailyStat.of(LocalDate.parse("2026-10-24"), 100, 50, 50, 0, 0, 0))
                .with(DailyStat.of(LocalDate.parse("2026-10-25"), 10, 1, 2, 3, 1, 3))
                .with(DailyStat.of(LocalDate.parse("2026-10-31"), 20, 2, 4, 6, 2, 6)));

        assertThat(stats.severity7d().critical()).isEqualTo(3);
        assertThat(stats.severity7d().high()).isEqualTo(6);
        assertThat(stats.severity7d().medium()).isEqualTo(9);
        assertThat(stats.severity7d().low()).isEqualTo(3);
        assertThat(stats.severity7d().unrated()).isEqualTo(9);
    }

    private StatsJson export(FakeDailyStatRepository repository) {
        new StatsFileExport(repository, writer).write(NOW);
        return writer.document("stats.json", StatsJson.class).orElseThrow();
    }

    private static DailyStat stat(String date, int newCount, int critical) {
        return DailyStat.of(LocalDate.parse(date), newCount, critical, newCount - critical, 0, 0, 0);
    }
}
