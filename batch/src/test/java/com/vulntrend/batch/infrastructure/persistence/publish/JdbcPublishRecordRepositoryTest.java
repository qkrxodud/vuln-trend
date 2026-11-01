package com.vulntrend.batch.infrastructure.persistence.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.publish.PublishRecord;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcPublishRecordRepository.class})
@DisplayName("마지막 게시 기록 — 실제 저장 구조에서")
class JdbcPublishRecordRepositoryTest {

    // 한국 시각 2026-11-02 08:00
    private static final Instant MORNING = Instant.parse("2026-11-01T23:00:00Z");
    private static final String VIEW = "a".repeat(64);

    @Autowired
    private JdbcPublishRecordRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("한 번도 게시하지 않았으면 마지막 게시 기록이 없다")
    void neverPublished() {
        assertThat(repository.findLast()).isEmpty();
    }

    @Test
    @DisplayName("남긴 게시 기록을 다시 찾으면 한국 날짜·지문·시각이 그대로 나온다")
    void savedIsFoundAgain() {
        repository.save(PublishRecord.published(MORNING, VIEW));

        PublishRecord found = repository.findLast().orElseThrow();

        assertThat(found.getPublishedOn()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(found.getFingerprint()).isEqualTo(VIEW);
        assertThat(found.getPublishedAt()).isEqualTo(MORNING);
    }

    @Test
    @DisplayName("다시 게시하면 기록이 한 줄로 남고 마지막 게시로 바뀐다")
    void saveOverwrites() {
        repository.save(PublishRecord.published(MORNING, VIEW));

        repository.save(PublishRecord.published(MORNING.plusSeconds(7200), "b".repeat(64)));

        assertThat(jdbc.queryForObject("select count(*) from publish_state", Integer.class)).isEqualTo(1);
        assertThat(repository.findLast().orElseThrow().getFingerprint()).isEqualTo("b".repeat(64));
        assertThat(repository.findLast().orElseThrow().getPublishedAt()).isEqualTo(MORNING.plusSeconds(7200));
    }
}
