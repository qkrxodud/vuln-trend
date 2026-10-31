package com.vulntrend.batch.application.score;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.fake.FakeTrendScoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("점수 다시 매기기")
class ScoreServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Test
    @DisplayName("이번 실행 시각을 기준으로 모든 CVE 의 점수를 다시 매기고 매긴 수를 알린다")
    void rescoresAtThisRun() {
        FakeTrendScoreRepository repository = new FakeTrendScoreRepository(1200);

        int rescored = new ScoreService(repository, Clock.fixed(NOW, ZoneOffset.UTC)).rescoreAll();

        assertThat(rescored).isEqualTo(1200);
        assertThat(repository.rescoredAt()).isEqualTo(NOW);
    }
}
