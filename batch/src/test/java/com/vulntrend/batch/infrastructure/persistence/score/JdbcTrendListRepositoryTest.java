package com.vulntrend.batch.infrastructure.persistence.score;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.vulnerability.Severity;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcTrendListRepository.class, JdbcTrendScoreRepository.class})
@DisplayName("트렌드 목록 — 실제 저장 구조에서")
class JdbcTrendListRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Autowired
    private JdbcTrendListRepository repository;

    @Autowired
    private JdbcTrendScoreRepository scores;

    @Autowired
    private JdbcTemplate jdbc;

    @Nested
    @DisplayName("순서는")
    class Order {

        @Test
        @DisplayName("점수 높은 순, 같으면 최근 발행 순, 그것도 같으면 번호 순이다")
        void scoreThenPublishedThenId() {
            scored("CVE-2026-60001", "50", "2026-10-30T00:00:00Z");
            scored("CVE-2026-60003", "70", "2026-10-29T00:00:00Z");
            scored("CVE-2026-60002", "50", "2026-10-30T00:00:00Z");
            scored("CVE-2026-60004", "50", "2026-10-30T05:00:00Z");

            assertThat(ids(repository.top(10, NOW)))
                    .containsExactly("CVE-2026-60003", "CVE-2026-60004", "CVE-2026-60001", "CVE-2026-60002");
        }

        @Test
        @DisplayName("점수나 발행일이 아직 없는 CVE 는 맨 뒤로 간다")
        void missingValuesLast() {
            scored("CVE-2026-60001", null, "2026-10-30T00:00:00Z");
            scored("CVE-2026-60002", "10", null);
            scored("CVE-2026-60003", "10", "2026-10-01T00:00:00Z");

            assertThat(ids(repository.top(10, NOW)))
                    .containsExactly("CVE-2026-60003", "CVE-2026-60002", "CVE-2026-60001");
        }

        @Test
        @DisplayName("철회된 CVE 는 빼고 요청한 수만큼만 준다")
        void listedOnlyUpToLimit() {
            scored("CVE-2026-60001", "90", "2026-10-30T00:00:00Z");
            jdbc.update("update vulnerability set rejected = true where vuln_id = 'CVE-2026-60001'");
            scored("CVE-2026-60002", "80", "2026-10-30T00:00:00Z");
            scored("CVE-2026-60003", "70", "2026-10-30T00:00:00Z");

            assertThat(ids(repository.top(1, NOW))).containsExactly("CVE-2026-60002");
        }
    }

    @Test
    @DisplayName("발행 구간은 시작을 넣고 끝을 빼서 고른다")
    void publishedBetweenHalfOpen() {
        scored("CVE-2026-60001", "10", "2026-10-24T15:00:00Z");
        scored("CVE-2026-60002", "20", "2026-10-24T14:59:59Z");
        scored("CVE-2026-60003", "30", "2026-10-31T00:00:00Z");
        scored("CVE-2026-60004", "40", "2026-10-30T23:59:59Z");

        List<TrendListing> recent = repository.publishedBetween(Instant.parse("2026-10-24T15:00:00Z"), NOW, 10, NOW);

        assertThat(ids(recent)).containsExactly("CVE-2026-60004", "CVE-2026-60001");
    }

    @Test
    @DisplayName("한 줄에 KEV·악용 확률·심각도·대표 제품·요약과 저장된 점수와 같은 식의 점수 구성을 담는다")
    void readsEveryValue() {
        jdbc.update("insert into vulnerability (vuln_id, title, published_at, is_kev, kev_added_at, kev_due_date, "
                        + "kev_ransomware, poc_count, mention_count, epss_score, epss_percentile, cvss_score, "
                        + "cvss_version, cvss_severity, primary_product) values ('CVE-2026-41203', 'V8 타입 혼동', ?, "
                        + "true, '2026-10-19', '2026-11-09', true, 0, 2, 0.94310, 0.99900, 9.8, '3.1', 'CRITICAL', "
                        + "'Chrome')", time("2026-10-18T00:00:00Z"));
        jdbc.update("insert into summary (vuln_id, what) values ('CVE-2026-41203', '악성 웹페이지로 원격 코드 실행')");
        scores.rescoreAll(NOW);

        TrendListing listing = repository.top(1, NOW).get(0);

        assertThat(listing.title()).isEqualTo("V8 타입 혼동");
        assertThat(listing.kev()).isTrue();
        assertThat(listing.kevRansomware()).isTrue();
        assertThat(listing.kevDueDate()).isEqualTo(LocalDate.parse("2026-11-09"));
        assertThat(listing.kevAddedAt()).isEqualTo(LocalDate.parse("2026-10-19"));
        assertThat(listing.mentionCount()).isEqualTo(2);
        assertThat(listing.epss()).isEqualByComparingTo("0.94310");
        assertThat(listing.epssPercentile()).isEqualByComparingTo("0.99900");
        assertThat(listing.cvss()).isEqualByComparingTo("9.8");
        assertThat(listing.cvssVersion()).isEqualTo("3.1");
        assertThat(listing.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(listing.primaryProduct()).isEqualTo("Chrome");
        assertThat(listing.summaryWhat()).isEqualTo("악성 웹페이지로 원격 코드 실행");
        assertThat(listing.publishedAt()).isEqualTo(Instant.parse("2026-10-18T00:00:00Z"));
        assertThat(listing.breakdown().getKev()).isEqualByComparingTo("50");
        assertThat(listing.breakdown().getRansomware()).isEqualByComparingTo("10");
        assertThat(listing.breakdown().total().setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(
                jdbc.queryForObject("select trend_score from vulnerability", BigDecimal.class));
    }

    @Test
    @DisplayName("심각도가 비어 있으면 미평가로, 요약이 없으면 비워서 읽는다")
    void unratedWithoutSummary() {
        scored("CVE-2026-60001", "10", "2026-10-30T00:00:00Z");

        TrendListing listing = repository.top(1, NOW).get(0);

        assertThat(listing.severity()).isEqualTo(Severity.UNRATED);
        assertThat(listing.summaryWhat()).isNull();
        assertThat(listing.epss()).isNull();
        assertThat(listing.kev()).isFalse();
    }

    private void scored(String id, String score, String published) {
        jdbc.update("insert into vulnerability (vuln_id, trend_score, published_at) values (?, ?, ?)", id,
                Objects.isNull(score) ? null : new BigDecimal(score), time(published));
    }

    private static Object time(String text) {
        return Objects.isNull(text) ? null : Instant.parse(text).atOffset(ZoneOffset.UTC);
    }

    private static List<String> ids(List<TrendListing> listings) {
        return listings.stream().map(listing -> listing.id().getValue()).toList();
    }
}
