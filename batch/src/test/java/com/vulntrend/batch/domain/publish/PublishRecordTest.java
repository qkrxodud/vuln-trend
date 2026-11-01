package com.vulntrend.batch.domain.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("마지막 게시 기록")
class PublishRecordTest {

    private static final String VIEW = "a".repeat(64);

    @Test
    @DisplayName("게시한 날짜는 한국 날짜로 남는다")
    void koreanDate() {
        // 한국 시각 2026-11-02 08:00 — UTC 로는 11월 1일
        PublishRecord record = PublishRecord.published(Instant.parse("2026-11-01T23:00:00Z"), VIEW);

        assertThat(record.getPublishedOn()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(record.getPublishedAt()).isEqualTo(Instant.parse("2026-11-01T23:00:00Z"));
        assertThat(record.getFingerprint()).isEqualTo(VIEW);
    }

    @Test
    @DisplayName("화면 지문이 비어 있으면 기록하지 않는다")
    void blankFingerprint() {
        assertThatThrownBy(() -> PublishRecord.published(Instant.parse("2026-11-01T00:00:00Z"), " "))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("화면 지문이 64자를 넘으면 기록하지 않는다")
    void tooLongFingerprint() {
        assertThatThrownBy(() -> PublishRecord.published(Instant.parse("2026-11-01T00:00:00Z"), "a".repeat(65)))
                .isInstanceOf(DomainValidationException.class);
    }
}
