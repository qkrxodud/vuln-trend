package com.vulntrend.batch.application.stat;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.fake.FakeDailyStatRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("일별 통계 다시 세기")
class DailyStatServiceTest {

    @Test
    @DisplayName("이번 실행 시각의 한국 달력일까지 최근 14일을 다시 세고 센 날 수를 알린다")
    void recalculatesRecentDays() {
        FakeDailyStatRepository repository = new FakeDailyStatRepository();
        Clock clock = Clock.fixed(Instant.parse("2026-10-30T23:00:00Z"), ZoneOffset.UTC);

        int days = new DailyStatService(repository, clock).recalculateRecent();

        assertThat(days).isEqualTo(14);
        assertThat(repository.recalculated().getToday()).isEqualTo(LocalDate.parse("2026-10-31"));
    }
}
