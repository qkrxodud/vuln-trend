package com.vulntrend.batch.domain.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("게시할지 정하기")
class PublishDecisionTest {

    private static final String VIEW = "a".repeat(64);
    private static final String OTHER_VIEW = "b".repeat(64);
    // 한국 시각 2026-11-01 09:00
    private static final Instant MORNING = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    @DisplayName("게시한 적이 한 번도 없으면 그날 첫 게시로 올린다")
    void neverPublished() {
        PublishDecision decision = PublishDecision.decide(Optional.empty(), MORNING, VIEW);

        assertThat(decision).isEqualTo(PublishDecision.FIRST_OF_DAY);
        assertThat(decision.isSkipped()).isFalse();
        assertThat(decision.rebuildsSearchIndex()).isTrue();
    }

    @Nested
    @DisplayName("오늘 이미 게시했으면")
    class PublishedToday {

        private final Optional<PublishRecord> last = Optional.of(PublishRecord.published(MORNING, VIEW));

        @Test
        @DisplayName("화면이 그대로인 실행은 게시를 건너뛴다")
        void sameViewIsSkipped() {
            PublishDecision decision = PublishDecision.decide(last, MORNING.plusSeconds(7200), VIEW);

            assertThat(decision).isEqualTo(PublishDecision.NO_CHANGE);
            assertThat(decision.isSkipped()).isTrue();
        }

        @Test
        @DisplayName("화면이 바뀐 실행은 게시하되 검색 색인은 다시 만들지 않는다")
        void changedViewIsPublishedWithoutSearchIndex() {
            PublishDecision decision = PublishDecision.decide(last, MORNING.plusSeconds(7200), OTHER_VIEW);

            assertThat(decision).isEqualTo(PublishDecision.VIEW_CHANGED);
            assertThat(decision.isSkipped()).isFalse();
            assertThat(decision.rebuildsSearchIndex()).isFalse();
        }

        @Test
        @DisplayName("한국 시각으로 자정을 넘기면 화면이 그대로여도 그날 첫 게시로 올린다(기한·오늘 표시가 바뀌므로)")
        void nextKoreanDayIsFirstOfDay() {
            // 한국 시각 2026-11-02 00:00 — UTC 로는 아직 11월 1일이다
            Instant koreanMidnight = Instant.parse("2026-11-01T15:00:00Z");

            PublishDecision decision = PublishDecision.decide(last, koreanMidnight, VIEW);

            assertThat(decision).isEqualTo(PublishDecision.FIRST_OF_DAY);
            assertThat(decision.rebuildsSearchIndex()).isTrue();
        }

        @Test
        @DisplayName("한국 시각으로 같은 날 23시 59분이면 아직 오늘이다")
        void lateEveningIsStillToday() {
            Instant lateEvening = Instant.parse("2026-11-01T14:59:00Z");

            assertThat(PublishDecision.decide(last, lateEvening, VIEW)).isEqualTo(PublishDecision.NO_CHANGE);
        }
    }
}
