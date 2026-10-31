package com.vulntrend.batch.domain.stat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("KPI 를 세는 24시간 창")
class KpiWindowTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Test
    @DisplayName("지금 창은 실행 시각에서 24시간을 거슬러 올라간다")
    void today() {
        KpiWindow window = KpiWindow.today(NOW);

        assertThat(window.getFrom()).isEqualTo(Instant.parse("2026-10-30T00:00:00Z"));
        assertThat(window.getTo()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("어제 창은 48시간 전부터 24시간 전까지다")
    void yesterday() {
        KpiWindow window = KpiWindow.yesterday(NOW);

        assertThat(window.getFrom()).isEqualTo(Instant.parse("2026-10-29T00:00:00Z"));
        assertThat(window.getTo()).isEqualTo(Instant.parse("2026-10-30T00:00:00Z"));
    }

    @Nested
    @DisplayName("한 창 안에 새로 들어온 수는")
    class Arrivals {

        @Test
        @DisplayName("신규 중 Critical 이 신규보다 많을 수 없다")
        void criticalWithinNew() {
            assertThatThrownBy(() -> NewArrivals.of(3, 0, 4)).isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("음수일 수 없다")
        void notNegative() {
            assertThatThrownBy(() -> NewArrivals.of(3, -1, 0)).isInstanceOf(DomainValidationException.class);
        }
    }
}
