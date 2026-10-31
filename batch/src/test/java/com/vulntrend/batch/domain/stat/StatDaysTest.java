package com.vulntrend.batch.domain.stat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("일별 통계를 다시 셀 날들")
class StatDaysTest {

    @Test
    @DisplayName("오늘을 포함해 최근 14일을 다시 센다")
    void fourteenDaysEndingToday() {
        StatDays days = StatDays.recent(Instant.parse("2026-10-31T00:00:00Z"));

        assertThat(days.getToday()).isEqualTo(LocalDate.parse("2026-10-31"));
        assertThat(days.getFirst()).isEqualTo(LocalDate.parse("2026-10-18"));
        assertThat(days.count()).isEqualTo(14);
    }

    @Test
    @DisplayName("오늘은 한국 시각의 달력일이라 세계 표준시 15시부터 다음 날이 된다")
    void todayIsKoreanCalendarDay() {
        assertThat(StatDays.recent(Instant.parse("2026-10-30T15:00:00Z")).getToday())
                .isEqualTo(LocalDate.parse("2026-10-31"));
        assertThat(StatDays.recent(Instant.parse("2026-10-30T14:59:59Z")).getToday())
                .isEqualTo(LocalDate.parse("2026-10-30"));
    }

    @Test
    @DisplayName("달이 바뀌어도 14일을 거슬러 센다")
    void acrossMonths() {
        StatDays days = StatDays.recent(Instant.parse("2026-11-03T00:00:00Z"));

        assertThat(days.getFirst()).isEqualTo(LocalDate.parse("2026-10-21"));
    }
}
