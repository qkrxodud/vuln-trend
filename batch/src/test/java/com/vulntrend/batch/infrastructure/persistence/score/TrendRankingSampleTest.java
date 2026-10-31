package com.vulntrend.batch.infrastructure.persistence.score;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 순위 검증(WBS 3.5). 실제 NVD·KEV·EPSS 를 이은 표본(test/resources/score/ranking-sample.csv, 머리말에 출처와 줄인 방법)으로
 * 점수를 매겨 상위 20건이 "지금 중요한가" 기준에 맞는지 고정한다. 실데이터 1년치 적재 뒤 육안 확인과 10초 실측은 따로 한다.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcTrendScoreRepository.class})
@DisplayName("트렌드 순위 — 실제 표본에서")
class TrendRankingSampleTest {

    /** 표본을 만든 날의 09시(한국) 실행 */
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String SAMPLE = "/score/ranking-sample.csv";
    private static final List<String> FAMOUS_OLD = List.of("CVE-2021-44228", "CVE-2017-0144", "CVE-2014-0160",
            "CVE-2023-4966", "CVE-2021-26855");

    @Autowired
    private JdbcTrendScoreRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void sample() throws IOException {
        List<Object[]> rows = lines().stream().skip(1).map(TrendRankingSampleTest::row).toList();
        jdbc.batchUpdate("insert into vulnerability (vuln_id, published_at, cvss_score, cvss_severity, is_kev, "
                + "kev_added_at, kev_ransomware, epss_score, epss_percentile) "
                + "values (?, cast(? as timestamptz), ?, ?, ?, cast(? as date), ?, ?, ?)", rows);
        repository.rescoreAll(NOW);
    }

    @Test
    @DisplayName("Log4Shell 처럼 오래된 유명 CVE 는 상위 100건에도 들지 않는다")
    void famousOldOnesAreNotOnTop() {
        assertThat(top(100)).doesNotContainAnyElementsOf(FAMOUS_OLD);
    }

    @Test
    @DisplayName("최근 7일 안에 KEV 에 오른 CVE 는 모두 상위 20건에 든다")
    void recentKevIsOnTop() {
        List<String> recentKev = jdbc.queryForList("select vuln_id from vulnerability where is_kev "
                + "and kev_added_at >= cast(? as date)", String.class, LocalDate.parse("2026-10-24"));

        assertThat(recentKev).isNotEmpty();
        assertThat(top(20)).containsAll(recentKev);
    }

    @Test
    @DisplayName("상위 20건은 모두 최근 30일 안에 발행됐거나 KEV 에 올랐다")
    void topTwentyIsRecent() {
        Instant since = NOW.minus(Duration.ofDays(30));
        List<Map<String, Object>> stale = jdbc.queryForList("select vuln_id from vulnerability "
                + "where vuln_id in (" + placeholders(20) + ") "
                + "and coalesce(published_at, '-infinity') < ? "
                + "and coalesce(kev_added_at::timestamp at time zone 'UTC', '-infinity') < ?",
                concat(top(20), since.atOffset(ZoneOffset.UTC), since.atOffset(ZoneOffset.UTC)));

        assertThat(stale).isEmpty();
    }

    private List<String> top(int size) {
        return jdbc.queryForList("select vuln_id from vulnerability order by trend_score desc, vuln_id limit ?",
                String.class, size);
    }

    private static String placeholders(int size) {
        return String.join(", ", Collections.nCopies(size, "?"));
    }

    private static Object[] concat(List<String> ids, Object... more) {
        Object[] all = Arrays.copyOf(ids.toArray(), ids.size() + more.length);
        System.arraycopy(more, 0, all, ids.size(), more.length);
        return all;
    }

    private static List<String> lines() throws IOException {
        try (InputStream in = TrendRankingSampleTest.class.getResourceAsStream(SAMPLE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(Predicate.not(line -> line.startsWith("#")))
                    .toList();
        }
    }

    private static Object[] row(String line) {
        String[] cells = line.split(",", -1);
        return new Object[] {cells[0], blank(cells[1]), decimal(cells[2]), cells[3], Boolean.parseBoolean(cells[4]),
                blank(cells[5]), Boolean.parseBoolean(cells[6]), decimal(cells[7]), decimal(cells[8])};
    }

    private static String blank(String cell) {
        return cell.isEmpty() ? null : cell;
    }

    private static BigDecimal decimal(String cell) {
        return Optional.ofNullable(blank(cell)).map(BigDecimal::new).orElse(null);
    }
}
