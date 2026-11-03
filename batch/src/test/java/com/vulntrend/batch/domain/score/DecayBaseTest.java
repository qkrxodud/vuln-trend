package com.vulntrend.batch.domain.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("시간 감쇠 기준 시각")
class DecayBaseTest {

    /** 2026-11-03 01:00 KST */
    private static final Instant AFTER_MIDNIGHT_KST = Instant.parse("2026-11-02T16:00:00Z");

    @Nested
    @DisplayName("EPSS 가 제때 들어오고 있으면")
    class WithFreshEpss {

        @Test
        @DisplayName("마지막으로 반영한 EPSS 날짜의 세계 표준시 자정을 기준으로 센다")
        void countsFromEpssDate() {
            DecayBase base = DecayBase.of(Optional.of(LocalDate.parse("2026-11-02")), AFTER_MIDNIGHT_KST);

            assertThat(base.getAt()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
            assertThat(base.isFallback()).isFalse();
        }

        @Test
        @DisplayName("한국 날짜가 바뀌어도 새 EPSS 가 들어오기 전까지는 기준 시각이 그대로다")
        void staysUntilNextEpss() {
            Optional<LocalDate> epss = Optional.of(LocalDate.parse("2026-11-02"));

            DecayBase evening = DecayBase.of(epss, Instant.parse("2026-11-02T14:00:00Z"));
            DecayBase nextMorning = DecayBase.of(epss, Instant.parse("2026-11-03T02:00:00Z"));

            assertThat(nextMorning.getAt()).isEqualTo(evening.getAt());
        }

        @Test
        @DisplayName("EPSS 날짜가 한국 오늘보다 이틀 앞서 멈춰 있어도 아직 EPSS 날짜를 기준으로 센다")
        void twoDaysStaleStillUsesEpss() {
            // given: 한국 11-04 10:00, 마지막 EPSS 11-02
            DecayBase base = DecayBase.of(Optional.of(LocalDate.parse("2026-11-02")),
                    Instant.parse("2026-11-04T01:00:00Z"));

            assertThat(base.getAt()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
            assertThat(base.isFallback()).isFalse();
        }
    }

    @Nested
    @DisplayName("EPSS 가 이틀 넘게 끊기면")
    class WithStaleEpss {

        @Test
        @DisplayName("한국 오늘 자정을 기준으로 센다")
        void fallsBackToKoreanMidnight() {
            // given: 한국 11-05 10:00, 마지막 EPSS 11-02(사흘 전)
            DecayBase base = DecayBase.of(Optional.of(LocalDate.parse("2026-11-02")),
                    Instant.parse("2026-11-05T01:00:00Z"));

            assertThat(base.getAt()).isEqualTo(Instant.parse("2026-11-04T15:00:00Z"));
            assertThat(base.isFallback()).isTrue();
        }
    }

    @Nested
    @DisplayName("EPSS 를 한 번도 반영하지 않았으면")
    class WithoutEpss {

        @Test
        @DisplayName("한국 오늘 자정을 기준으로 센다")
        void fallsBackToKoreanMidnight() {
            DecayBase base = DecayBase.of(Optional.empty(), AFTER_MIDNIGHT_KST);

            assertThat(base.getAt()).isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
            assertThat(base.isFallback()).isTrue();
        }

        @Test
        @DisplayName("같은 한국 날짜 안의 실행은 모두 같은 기준 시각을 쓴다")
        void sameWithinKoreanDay() {
            DecayBase first = DecayBase.of(Optional.empty(), Instant.parse("2026-11-02T15:30:00Z"));
            DecayBase last = DecayBase.of(Optional.empty(), Instant.parse("2026-11-03T14:59:00Z"));

            assertThat(last.getAt()).isEqualTo(first.getAt());
        }
    }
}
