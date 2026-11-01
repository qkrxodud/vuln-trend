package com.vulntrend.batch.infrastructure.persistence.score;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.remedy.dto.RemedyListing;
import com.vulntrend.batch.domain.remedy.dto.TaggedLink;
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

        List<TrendListing> recent = repository.publishedBetween(Instant.parse("2026-10-24T15:00:00Z"), NOW, NOW);

        assertThat(ids(recent)).containsExactly("CVE-2026-60004", "CVE-2026-60001");
    }

    @Test
    @DisplayName("발행 구간의 CVE 는 점수와 상관없이 최근 발행 순(같으면 번호 순)으로 모두 준다")
    void publishedBetweenNewestFirst() {
        scored("CVE-2026-60001", "90", "2026-10-28T00:00:00Z");
        scored("CVE-2026-60002", "10", "2026-10-30T00:00:00Z");
        scored("CVE-2026-60004", "50", "2026-10-29T00:00:00Z");
        scored("CVE-2026-60003", "50", "2026-10-29T00:00:00Z");

        List<TrendListing> recent = repository.publishedBetween(Instant.parse("2026-10-24T15:00:00Z"), NOW, NOW);

        assertThat(ids(recent)).containsExactly("CVE-2026-60002", "CVE-2026-60003", "CVE-2026-60004",
                "CVE-2026-60001");
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

    @Nested
    @DisplayName("조치 정보는")
    class Remedy {

        @Test
        @DisplayName("대표 제품의 취약 범위, NVD 참고 링크의 주소와 태그(원본 순서), KEV 요구 조치를 원본에서 읽는다")
        void fromStoredSources() {
            jdbc.update("insert into vulnerability (vuln_id, trend_score, published_at, primary_product) "
                    + "values ('CVE-2026-41203', 10, ?, 'Chrome')", time("2026-10-18T00:00:00Z"));
            jdbc.update("insert into product_alias (cpe_vendor, cpe_product, slug, display_name) values "
                    + "('google', 'chrome', 'chrome', 'Chrome')");
            jdbc.update("insert into affected_product (vuln_id, vendor, product, slug, vulnerable_range) values "
                    + "('CVE-2026-41203', 'google', 'chrome', 'chrome', '<130.0.1'), "
                    + "('CVE-2026-41203', 'debian', 'debian_linux', null, '<12.0'), "
                    + "('CVE-2026-41203', 'google', 'chrome_os', null, null)");
            jdbc.update("insert into vulnerability_raw (vuln_id, source, payload) values ('CVE-2026-41203', 'nvd', "
                    + "cast(? as jsonb)), ('CVE-2026-41203', 'kev', cast(? as jsonb))",
                    "{\"references\": [{\"url\": \"https://b/advisory\", \"tags\": [\"Release Notes\", "
                            + "\"Vendor Advisory\"]}, {\"url\": \"https://a/issue\"}]}",
                    "{\"requiredAction\": \"Apply updates per vendor instructions.\"}");

            RemedyListing remedy = repository.top(1, NOW).get(0).remedy();

            assertThat(remedy.primaryRanges()).containsExactly("<130.0.1");
            assertThat(remedy.nvdReferences()).containsExactly(
                    new TaggedLink("https://b/advisory", List.of("Release Notes", "Vendor Advisory")),
                    new TaggedLink("https://a/issue", List.of()));
            assertThat(remedy.kevRequiredAction()).isEqualTo("Apply updates per vendor instructions.");
        }

        @Test
        @DisplayName("대표 제품이 CPE 원문이면 같은 제품 이름의 범위를 읽는다")
        void rawProductName() {
            jdbc.update("insert into vulnerability (vuln_id, trend_score, published_at, primary_product) "
                    + "values ('CVE-2026-41203', 10, ?, 'panorama')", time("2026-10-18T00:00:00Z"));
            jdbc.update("insert into affected_product (vuln_id, vendor, product, vulnerable_range) values "
                    + "('CVE-2026-41203', 'paloaltonetworks', 'panorama', '>=10.1, <10.1.14 || >=11.0, <11.0.6')");

            assertThat(repository.top(1, NOW).get(0).remedy().primaryRanges())
                    .containsExactly(">=10.1, <10.1.14 || >=11.0, <11.0.6");
        }

        @Test
        @DisplayName("원본이 없으면 범위·링크는 빈 목록, 요구 조치는 비워 둔다")
        void nothingStored() {
            scored("CVE-2026-60001", "10", "2026-10-30T00:00:00Z");

            RemedyListing remedy = repository.top(1, NOW).get(0).remedy();

            assertThat(remedy.primaryRanges()).isEmpty();
            assertThat(remedy.nvdReferences()).isEmpty();
            assertThat(remedy.kevRequiredAction()).isNull();
        }
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
