package com.vulntrend.batch.application.score;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.fake.FakeEpssRepository;
import com.vulntrend.batch.fake.FakeTrendScoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("점수 다시 매기기")
@ExtendWith(OutputCaptureExtension.class)
class ScoreServiceTest {

    /** 2026-11-03 01:00 KST */
    private static final Instant NOW = Instant.parse("2026-11-02T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Nested
    @DisplayName("EPSS 가 제때 들어오고 있으면")
    class WithFreshEpss {

        private final FakeEpssRepository epss = new FakeEpssRepository(Set.of()).appliedOn(LocalDate.parse("2026-11-02"));

        @Test
        @DisplayName("모든 CVE 의 점수를 마지막 EPSS 날짜 자정 기준 감쇠로 다시 매기고 매긴 수를 알린다")
        void rescoresAtEpssDate() {
            FakeTrendScoreRepository repository = new FakeTrendScoreRepository(1200);

            int rescored = new ScoreService(repository, epss, CLOCK).rescoreAll();

            assertThat(rescored).isEqualTo(1200);
            assertThat(repository.rescoredAt()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
        }

        @Test
        @DisplayName("사이트 파일도 같은 감쇠 기준 시각을 받는다")
        void sameBaseForSiteFiles() {
            ScoreService service = new ScoreService(new FakeTrendScoreRepository(0), epss, CLOCK);

            assertThat(service.decayBase().getAt()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
        }

        @Test
        @DisplayName("감쇠 기준을 대체하지 않았으면 실행 기록에 대체 문장을 남기지 않는다")
        void noFallbackLog(CapturedOutput output) {
            new ScoreService(new FakeTrendScoreRepository(0), epss, CLOCK).rescoreAll();

            assertThat(output.getAll()).doesNotContain("Decay base fell back");
        }
    }

    @Nested
    @DisplayName("EPSS 가 이틀 넘게 끊기면")
    class WithStaleEpss {

        private final FakeEpssRepository epss = new FakeEpssRepository(Set.of()).appliedOn(LocalDate.parse("2026-10-30"));

        @Test
        @DisplayName("한국 오늘 자정 기준 감쇠로 다시 매긴다")
        void rescoresAtKoreanMidnight() {
            FakeTrendScoreRepository repository = new FakeTrendScoreRepository(1200);

            new ScoreService(repository, epss, CLOCK).rescoreAll();

            assertThat(repository.rescoredAt()).isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
        }

        @Test
        @DisplayName("감쇠 기준을 대체했다고 실행 기록에 한 줄 남긴다")
        void logsFallback(CapturedOutput output) {
            new ScoreService(new FakeTrendScoreRepository(0), epss, CLOCK).rescoreAll();

            assertThat(output.getAll()).containsOnlyOnce(
                    "Decay base fell back to Korean midnight 2026-11-02T15:00:00Z because the last EPSS date is 2026-10-30.");
        }
    }

    @Test
    @DisplayName("EPSS 를 반영한 적이 없으면 대체 문장에 날짜 대신 없음을 적는다")
    void logsFallbackWithoutEpss(CapturedOutput output) {
        new ScoreService(new FakeTrendScoreRepository(0), new FakeEpssRepository(Set.of()), CLOCK).rescoreAll();

        assertThat(output.getAll()).containsOnlyOnce(
                "Decay base fell back to Korean midnight 2026-11-02T15:00:00Z because the last EPSS date is none.");
    }
}
