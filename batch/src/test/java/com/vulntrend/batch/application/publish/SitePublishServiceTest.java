package com.vulntrend.batch.application.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.vulntrend.batch.domain.publish.PublishDecision;
import com.vulntrend.batch.domain.publish.PublishRecord;
import com.vulntrend.batch.fake.FakePublishRecordRepository;
import com.vulntrend.batch.fake.FakePublisher;
import com.vulntrend.batch.global.error.exception.PublishException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사이트 게시")
class SitePublishServiceTest {

    // 한국 시각 2026-11-01 11:00
    private static final Instant NOW = Instant.parse("2026-11-01T02:00:00Z");
    private static final String VIEW = "a".repeat(64);

    private final FakePublishRecordRepository records = new FakePublishRecordRepository();
    private FakePublisher publisher = FakePublisher.working();

    @Test
    @DisplayName("마지막 게시와 지금 시각·지문을 비교해 이번 실행의 판정을 낸다")
    void decidesFromLastRecord() {
        records.save(PublishRecord.published(NOW.minusSeconds(7200), VIEW));

        assertThat(service().decide(VIEW)).isEqualTo(PublishDecision.NO_CHANGE);
        assertThat(service().decide("b".repeat(64))).isEqualTo(PublishDecision.VIEW_CHANGED);
    }

    @Test
    @DisplayName("올리고 나면 오늘 한국 날짜·지문·시각을 마지막 게시로 남긴다")
    void recordsAfterPublishing() {
        service().publish(VIEW);

        PublishRecord last = records.findLast().orElseThrow();
        assertThat(publisher.published()).isEqualTo(1);
        assertThat(last.getPublishedOn()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(last.getFingerprint()).isEqualTo(VIEW);
        assertThat(last.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("올리지 못하면 마지막 게시 기록을 바꾸지 않아 다음 실행이 다시 올린다")
    void failedPublishKeepsLastRecord() {
        records.save(PublishRecord.published(NOW.minusSeconds(7200), VIEW));
        publisher = FakePublisher.failing();

        Throwable failure = catchThrowable(() -> service().publish("b".repeat(64)));

        assertThat(failure).isInstanceOf(PublishException.class);
        assertThat(records.findLast().orElseThrow().getFingerprint()).isEqualTo(VIEW);
        assertThat(service().decide("b".repeat(64))).isEqualTo(PublishDecision.VIEW_CHANGED);
    }

    private SitePublishService service() {
        return new SitePublishService(publisher, records, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
