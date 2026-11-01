package com.vulntrend.batch.infrastructure.persistence.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.summary.KoreanSummary;
import com.vulntrend.batch.domain.summary.SummaryOutcome;
import com.vulntrend.batch.domain.summary.SummaryProduct;
import com.vulntrend.batch.domain.summary.SummarySource;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
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
@Import({PostgresTestConfig.class, JdbcSummaryRepository.class})
@DisplayName("한국어 요약 — 실제 저장 구조에서")
class JdbcSummaryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final KoreanSummary SUMMARY = KoreanSummary.of("원격 코드 실행 취약점이다.",
            "Log4j 2.15.0 미만 사용자가 영향받는다.", "원문 참고");

    @Autowired
    private JdbcSummaryRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Nested
    @DisplayName("요약할 CVE 고르기")
    class Targets {

        @Test
        @DisplayName("트렌드 점수 상위 건만 점수 순서대로 고른다")
        void topByTrendScore() {
            vulnerability("CVE-2026-0001", "30.00", false);
            vulnerability("CVE-2026-0002", "20.00", false);
            vulnerability("CVE-2026-0003", "10.00", false);

            assertThat(ids(repository.findTopWithoutSummary(2))).containsExactly("CVE-2026-0001", "CVE-2026-0002");
        }

        @Test
        @DisplayName("이미 요약이 있는 CVE 는 빼고, 그 자리를 다음 순위 CVE 로 채우지 않는다")
        void skipsSummarizedWithoutBackfilling() {
            vulnerability("CVE-2026-0001", "30.00", false);
            vulnerability("CVE-2026-0002", "20.00", false);
            vulnerability("CVE-2026-0003", "10.00", false);
            repository.saveSummary(CveId.from("CVE-2026-0001"), SUMMARY, "gpt-6-luna", NOW, "t", "t");

            assertThat(ids(repository.findTopWithoutSummary(2))).containsExactly("CVE-2026-0002");
        }

        @Test
        @DisplayName("요약을 버렸거나 거절된 CVE 는 다시 부르지 않는다")
        void skipsDiscardedAndRefused() {
            vulnerability("CVE-2026-0001", "30.00", false);
            vulnerability("CVE-2026-0002", "20.00", false);
            repository.recordCall(CveId.from("CVE-2026-0001"), SummaryOutcome.DISCARDED, "gpt-6-luna", NOW);
            repository.recordCall(CveId.from("CVE-2026-0002"), SummaryOutcome.REFUSED, "gpt-6-luna-2026-05-18", NOW);

            assertThat(repository.findTopWithoutSummary(20)).isEmpty();
        }

        @Test
        @DisplayName("요약하지 못하고 실패한 CVE 는 다음 실행에서 다시 고른다")
        void retriesFailures() {
            vulnerability("CVE-2026-0001", "30.00", false);
            repository.recordCall(CveId.from("CVE-2026-0001"), SummaryOutcome.FAILED, null, NOW);

            assertThat(ids(repository.findTopWithoutSummary(20))).containsExactly("CVE-2026-0001");
        }

        @Test
        @DisplayName("점수가 아직 매겨지지 않은 CVE 는 점수가 있는 CVE 를 밀어내고 상위에 들지 않는다")
        void unscoredDoesNotJumpAhead() {
            vulnerability("CVE-2026-0001", null, false);
            vulnerability("CVE-2026-0002", "20.00", false);

            assertThat(ids(repository.findTopWithoutSummary(1))).containsExactly("CVE-2026-0002");
        }

        @Test
        @DisplayName("철회 표시가 비어 있는 CVE 는 철회되지 않은 것으로 본다")
        void missingRejectedFlagMeansListed() {
            vulnerability("CVE-2026-0001", "30.00", false);
            jdbc.update("update vulnerability set rejected = null where vuln_id = 'CVE-2026-0001'");

            assertThat(ids(repository.findTopWithoutSummary(1))).containsExactly("CVE-2026-0001");
        }

        @Test
        @DisplayName("NVD 가 철회한 CVE 는 상위 목록에 들지 않는다")
        void excludesRejected() {
            vulnerability("CVE-2026-0001", "30.00", true);
            vulnerability("CVE-2026-0002", "20.00", false);

            assertThat(ids(repository.findTopWithoutSummary(1))).containsExactly("CVE-2026-0002");
        }

        @Test
        @DisplayName("원문 설명·영향 제품·버전 범위·고친 버전·심각도 점수를 함께 가져온다")
        void carriesSource() {
            vulnerability("CVE-2021-44228", "50.00", false);
            jdbc.update("insert into affected_product (vuln_id, vendor, product, vulnerable_range, fixed_version) "
                    + "values ('CVE-2021-44228', 'apache', 'log4j', '>= 2.0-beta9, < 2.15.0', '2.15.0')");

            SummarySource source = repository.findTopWithoutSummary(20).get(0);

            assertThat(source.getDescription()).isEqualTo("Description of CVE-2021-44228. More text.");
            assertThat(source.getCvssScore()).contains(new BigDecimal("9.8"));
            assertThat(source.getProducts()).singleElement().satisfies(product -> {
                assertThat(product.getVendor()).isEqualTo("apache");
                assertThat(product.getVulnerableRange()).contains(">= 2.0-beta9, < 2.15.0");
                assertThat(product.getFixedVersion()).contains("2.15.0");
            });
        }

        @Test
        @DisplayName("영향 제품을 모르는 CVE 도 설명만으로 고른다")
        void withoutProducts() {
            vulnerability("CVE-2026-0001", "30.00", false);

            assertThat(repository.findTopWithoutSummary(20)).singleElement()
                    .extracting(SummarySource::getProducts).asList().isEmpty();
        }
    }

    @Nested
    @DisplayName("오늘 호출 세기")
    class CountingCalls {

        @Test
        @DisplayName("답을 받은 호출은 하루가 시작된 뒤에 만들고 버리고 거절된 것만 센다")
        void countsAnsweredCallsSinceStartOfDay() {
            callsOfEveryOutcome();

            assertThat(repository.countAnsweredSince(NOW)).isEqualTo(3);
        }

        @Test
        @DisplayName("실패한 호출은 하루가 시작된 뒤의 실패만 따로 센다")
        void countsFailedCallsSinceStartOfDay() {
            callsOfEveryOutcome();

            assertThat(repository.countFailedSince(NOW)).isEqualTo(1);
        }

        // 하루 시작 전 실패 1건·생성 1건, 시작 뒤 생성·버림·거절·실패 1건씩
        private void callsOfEveryOutcome() {
            for (int i = 1; i <= 6; i++) {
                vulnerability("CVE-2026-000" + i, "%d.00".formatted(i), false);
            }
            repository.recordCall(CveId.from("CVE-2026-0001"), SummaryOutcome.FAILED, null, NOW.minusSeconds(1));
            repository.recordCall(CveId.from("CVE-2026-0002"), SummaryOutcome.GENERATED, "gpt-6-luna",
                    NOW.minusSeconds(1));
            repository.recordCall(CveId.from("CVE-2026-0003"), SummaryOutcome.GENERATED, "gpt-6-luna", NOW);
            repository.recordCall(CveId.from("CVE-2026-0004"), SummaryOutcome.DISCARDED, "gpt-6-luna", NOW);
            repository.recordCall(CveId.from("CVE-2026-0005"), SummaryOutcome.REFUSED, "gpt-6-luna", NOW);
            repository.recordCall(CveId.from("CVE-2026-0006"), SummaryOutcome.FAILED, null, NOW);
        }
    }

    @Nested
    @DisplayName("요약을 남기면")
    class Saving {

        @Test
        @DisplayName("세 문장과 실제로 답한 모델 이름, 만든 시각이 남는다")
        void savesSummary() {
            vulnerability("CVE-2026-0001", "30.00", false);

            repository.saveSummary(CveId.from("CVE-2026-0001"), SUMMARY, "gpt-6-luna-2026-05-18", NOW, "t", "t");

            assertThat(jdbc.queryForMap("select what, who, action, model, generated_at from summary"))
                    .containsEntry("what", "원격 코드 실행 취약점이다.")
                    .containsEntry("model", "gpt-6-luna-2026-05-18");
            assertThat(jdbc.queryForObject("select generated_at from summary", OffsetDateTime.class).toInstant())
                    .isEqualTo(NOW);
        }

        @Test
        @DisplayName("한 번 만든 요약은 다시 만들어도 바뀌지 않는다")
        void neverRegenerates() {
            vulnerability("CVE-2026-0001", "30.00", false);
            repository.saveSummary(CveId.from("CVE-2026-0001"), SUMMARY, "gpt-6-luna", NOW, "t", "t");

            repository.saveSummary(CveId.from("CVE-2026-0001"), KoreanSummary.of("다른 문장이다.", "누구", "원문 참고"),
                    "gpt-6-luna-2026-05-18", NOW.plusSeconds(60), "t", "t");

            assertThat(jdbc.queryForObject("select what from summary", String.class)).isEqualTo("원격 코드 실행 취약점이다.");
        }

        @Test
        @DisplayName("제목이 설명에서 만든 임시 제목이면 요약의 무엇이 문제인가 문장으로 바꾼다")
        void raisesDescriptionTitle() {
            vulnerability("CVE-2026-0001", "30.00", false);

            repository.saveSummary(CveId.from("CVE-2026-0001"), SUMMARY, "gpt-6-luna", NOW,
                    "Description of CVE-2026-0001", "원격 코드 실행 취약점이다");

            assertThat(title("CVE-2026-0001")).isEqualTo("원격 코드 실행 취약점이다");
        }

        @Test
        @DisplayName("GHSA 처럼 더 앞선 출처가 고친 제목은 덮지 않는다")
        void keepsBetterTitle() {
            vulnerability("CVE-2026-0001", "30.00", false);
            jdbc.update("update vulnerability set title = 'Remote code execution in Foo' where vuln_id = 'CVE-2026-0001'");

            repository.saveSummary(CveId.from("CVE-2026-0001"), SUMMARY, "gpt-6-luna", NOW,
                    "Description of CVE-2026-0001", "원격 코드 실행 취약점이다");

            assertThat(title("CVE-2026-0001")).isEqualTo("Remote code execution in Foo");
        }
    }

    private void vulnerability(String id, String trendScore, boolean rejected) {
        jdbc.update("insert into vulnerability (vuln_id, title, description, cvss_score, trend_score, published_at, "
                        + "rejected) values (?, ?, ?, 9.8, ?, ?, ?)",
                id, "Description of " + id, "Description of " + id + ". More text.",
                Objects.isNull(trendScore) ? null : new BigDecimal(trendScore),
                NOW.minusSeconds(3600).atOffset(ZoneOffset.UTC), rejected);
    }

    private String title(String id) {
        return jdbc.queryForObject("select title from vulnerability where vuln_id = ?", String.class, id);
    }

    private static List<String> ids(List<SummarySource> sources) {
        return sources.stream().map(source -> source.getCveId().getValue()).toList();
    }
}
