package com.vulntrend.batch.infrastructure.persistence.score;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.export.dto.CategoryCount;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcCategoryRepository.class})
@DisplayName("분류별 건수 — 실제 저장 구조에서")
class JdbcCategoryRepositoryTest {

    private static final Instant WEEK_FROM = Instant.parse("2026-10-24T15:00:00Z");
    private static final Instant MONTH_FROM = Instant.parse("2026-10-01T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Autowired
    private JdbcCategoryRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void vulnerabilities() {
        cve("CVE-2026-60001", "2026-10-30T00:00:00Z", "browser", "{rce,memory}", false);
        cve("CVE-2026-60002", "2026-10-24T15:00:00Z", "browser", "{rce}", true);
        cve("CVE-2026-60003", "2026-10-24T14:59:59Z", "browser", "{}", false);
        cve("CVE-2026-60004", "2026-10-01T14:59:59Z", "browser", "{rce}", true);
        cve("CVE-2026-60005", "2026-10-30T00:00:00Z", null, "{}", false);
        cve("CVE-2026-60006", "2026-10-30T00:00:00Z", "browser", "{rce}", true);
        jdbc.update("update vulnerability set rejected = true where vuln_id = 'CVE-2026-60006'");
    }

    @Test
    @DisplayName("제품군마다 7일·30일 창(시작은 넣고 끝은 뺀다)의 발행 수와 KEV 전체 수를 센다 — 철회 건과 분류 전은 빠진다")
    void familyCounts() {
        Map<String, CategoryCount> counts = repository.familyCounts(WEEK_FROM, MONTH_FROM, NOW);

        assertThat(counts).containsOnlyKeys("browser");
        assertThat(counts.get("browser")).isEqualTo(new CategoryCount(2, 3, 2));
    }

    @Test
    @DisplayName("공격 유형마다 같은 수를 세고, 여러 유형이 붙은 CVE 는 유형마다 센다")
    void attackTypeCounts() {
        Map<String, CategoryCount> counts = repository.attackTypeCounts(WEEK_FROM, MONTH_FROM, NOW);

        assertThat(counts).containsOnlyKeys("rce", "memory");
        assertThat(counts.get("rce")).isEqualTo(new CategoryCount(2, 2, 2));
        assertThat(counts.get("memory")).isEqualTo(new CategoryCount(1, 1, 0));
    }

    private void cve(String id, String published, String family, String attackTypes, boolean kev) {
        jdbc.update("insert into vulnerability (vuln_id, published_at, product_family, attack_types, is_kev) "
                + "values (?, ?, ?, cast(? as text[]), ?)", id, Instant.parse(published).atOffset(ZoneOffset.UTC),
                family, attackTypes, kev);
    }
}
