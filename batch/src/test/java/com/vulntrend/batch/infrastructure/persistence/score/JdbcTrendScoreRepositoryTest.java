package com.vulntrend.batch.infrastructure.persistence.score;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.score.ScoreBreakdown;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
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
@Import({PostgresTestConfig.class, JdbcTrendScoreRepository.class})
@DisplayName("트렌드 점수 — 실제 저장 구조에서")
class JdbcTrendScoreRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Autowired
    private JdbcTrendScoreRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void vulnerabilities() {
        vulnerability("CVE-2026-20001", true, true, "0.90000", "9.8", NOW, null, null, false);
        vulnerability("CVE-2026-20002", false, false, "0.50000", "7.5", NOW.minus(days(30)), null, null, false);
        vulnerability("CVE-2026-20003", false, false, null, null, NOW.minus(days(1)), null, null, false);
        vulnerability("CVE-2021-44228", true, false, "0.99999", "10.0", Instant.parse("2021-12-10T00:00:00Z"),
                "2026-10-01", null, false);
        vulnerability("CVE-2026-20005", false, false, null, "8.0", NOW.minus(days(60)), null, NOW, false);
        vulnerability("CVE-2026-20006", false, false, null, "5.0", NOW.plus(days(1)), null, null, false);
        vulnerability("CVE-2026-20007", false, false, null, "6.0", null, null, null, false);
        vulnerability("CVE-2026-20008", true, true, "0.90000", "9.8", NOW, null, null, true);
        jdbc.update("update vulnerability set trend_score = 77 where vuln_id = 'CVE-2026-20008'");
    }

    @Nested
    @DisplayName("점수를 다시 매기면")
    class Rescoring {

        @Test
        @DisplayName("KEV 50, 랜섬웨어 10, EPSS×30, CVSS 를 더한다")
        void addsFourTerms() {
            repository.rescoreAll(NOW);

            assertThat(score("CVE-2026-20001")).isEqualByComparingTo("96.80");
        }

        @Test
        @DisplayName("발행 뒤 30일마다 점수가 절반으로 줄어든다")
        void halvesEveryThirtyDays() {
            repository.rescoreAll(NOW);

            assertThat(score("CVE-2026-20002")).isEqualByComparingTo("11.25");
        }

        @Test
        @DisplayName("악용 확률과 심각도 점수가 없으면 그 항은 0 으로 센다")
        void missingTermsCountAsZero() {
            repository.rescoreAll(NOW);

            assertThat(score("CVE-2026-20003")).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("오래된 CVE 가 KEV 에 새로 오르면 등재일부터 다시 감쇠해 상위로 떠오른다")
        void kevListingResetsAge() {
            repository.rescoreAll(NOW);

            // 2026-10-01 등재 → 30일 → (50 + 29.9997 + 10) × 0.5
            assertThat(score("CVE-2021-44228")).isEqualByComparingTo("45.00");
        }

        @Test
        @DisplayName("KEV 등재일은 저장소의 시간대 설정과 상관없이 세계 표준시 자정으로 센다")
        void kevDateIsUtcMidnight() {
            jdbc.execute("set time zone 'Asia/Seoul'");

            repository.rescoreAll(NOW);

            assertThat(score("CVE-2021-44228")).isEqualByComparingTo("45.00");
        }

        @Test
        @DisplayName("최근 뉴스 언급이 있으면 언급 시각부터 감쇠한다")
        void mentionResetsAge() {
            repository.rescoreAll(NOW);

            assertThat(score("CVE-2026-20005")).isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("발행 시각이 이번 실행보다 늦어도 점수가 감쇠 전보다 커지지 않는다")
        void futureDoesNotGrow() {
            repository.rescoreAll(NOW);

            assertThat(score("CVE-2026-20006")).isEqualByComparingTo("5.00");
        }

        @Test
        @DisplayName("기준 시각을 하나도 알 수 없으면 감쇠 없이 매긴다")
        void unknownAgeHasNoDecay() {
            repository.rescoreAll(NOW);

            assertThat(score("CVE-2026-20007")).isEqualByComparingTo("6.00");
        }

        @Test
        @DisplayName("NVD 가 철회한 CVE 는 다시 매기지 않는다")
        void rejectedIsLeft() {
            int rescored = repository.rescoreAll(NOW);

            assertThat(rescored).isEqualTo(7);
            assertThat(score("CVE-2026-20008")).isEqualByComparingTo("77");
        }
    }

    @Nested
    @DisplayName("항별 기여도를 물으면")
    class Breakdown {

        @Test
        @DisplayName("항마다 몇 점을 보탰는지와 감쇠로 빠진 점수가 나오고 합이 저장된 점수와 같다")
        void breakdownMatchesStoredScore() {
            repository.rescoreAll(NOW);

            ScoreBreakdown log4shell = repository.breakdownsOf(List.of(CveId.from("CVE-2021-44228")), NOW)
                    .get(CveId.from("CVE-2021-44228"));

            assertThat(log4shell.getKev()).isEqualByComparingTo("50");
            assertThat(log4shell.getRansomware()).isEqualByComparingTo("0");
            assertThat(log4shell.getEpss()).isEqualByComparingTo("29.9997");
            assertThat(log4shell.getCvss()).isEqualByComparingTo("10.0");
            assertThat(log4shell.getDecay()).isEqualByComparingTo("-44.99985");
            assertThat(log4shell.total().setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo(score("CVE-2021-44228"));
        }

        @Test
        @DisplayName("물어본 CVE 중 목록에 없는 것은 나오지 않는다")
        void unknownIsAbsent() {
            Map<CveId, ScoreBreakdown> found = repository.breakdownsOf(
                    List.of(CveId.from("CVE-2026-20001"), CveId.from("CVE-2026-99999")), NOW);

            assertThat(found).containsOnlyKeys(CveId.from("CVE-2026-20001"));
        }

        @Test
        @DisplayName("물어본 CVE 가 없으면 아무것도 나오지 않는다")
        void nothingAsked() {
            assertThat(repository.breakdownsOf(List.of(), NOW)).isEmpty();
        }
    }

    private static Duration days(int n) {
        return Duration.ofDays(n);
    }

    private BigDecimal score(String id) {
        return jdbc.queryForObject("select trend_score from vulnerability where vuln_id = ?", BigDecimal.class, id);
    }

    private void vulnerability(String id, boolean kev, boolean ransomware, String epss, String cvss, Instant published,
            String kevAdded, Instant lastMention, boolean rejected) {
        jdbc.update("insert into vulnerability (vuln_id, is_kev, kev_ransomware, epss_score, cvss_score, published_at, "
                        + "kev_added_at, last_mention_at, rejected) values (?, ?, ?, ?, ?, ?, cast(? as date), ?, ?)",
                id, kev, ransomware, decimal(epss), decimal(cvss), time(published), kevAdded, time(lastMention),
                rejected);
    }

    private static BigDecimal decimal(String value) {
        return Objects.isNull(value) ? null : new BigDecimal(value);
    }

    private static OffsetDateTime time(Instant instant) {
        return Objects.isNull(instant) ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
