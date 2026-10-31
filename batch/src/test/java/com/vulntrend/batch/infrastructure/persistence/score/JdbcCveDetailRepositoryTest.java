package com.vulntrend.batch.infrastructure.persistence.score;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.export.dto.AffectedListing;
import com.vulntrend.batch.domain.export.dto.CveDetailListing;
import com.vulntrend.batch.domain.export.dto.ReferenceListing;
import com.vulntrend.batch.domain.export.dto.SearchListing;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.math.BigDecimal;
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
@Import({PostgresTestConfig.class, JdbcCveDetailRepository.class})
@DisplayName("상세·검색 조회 — 실제 저장 구조에서")
class JdbcCveDetailRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Autowired
    private JdbcCveDetailRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Nested
    @DisplayName("검색 색인은")
    class Search {

        @Test
        @DisplayName("철회되지 않은 전체 CVE 를 최근 발행 순으로, 발행일이 없으면 맨 뒤에 번호·제목·대표 제품으로 준다")
        void everyListedNewestFirst() {
            vulnerability("CVE-2026-70001", "10", "2026-10-29T00:00:00Z", "제목 1", "Chrome");
            vulnerability("CVE-2026-70002", "90", "2026-10-30T00:00:00Z", null, null);
            vulnerability("CVE-2026-70003", "50", null, "제목 3", null);
            vulnerability("CVE-2026-70004", "50", "2026-10-31T00:00:00Z", "철회", null);
            jdbc.update("update vulnerability set rejected = true where vuln_id = 'CVE-2026-70004'");

            assertThat(repository.searchEntries()).containsExactly(
                    new SearchListing(CveId.from("CVE-2026-70002"), null, null),
                    new SearchListing(CveId.from("CVE-2026-70001"), "제목 1", "Chrome"),
                    new SearchListing(CveId.from("CVE-2026-70003"), "제목 3", null));
        }
    }

    @Test
    @DisplayName("기간을 정한 검색 색인은 그 시각 이후 발행 건만 같은 순서로 주고 발행일이 없는 건은 뺀다")
    void searchEntriesSince() {
        vulnerability("CVE-2026-70001", "10", "2026-10-29T00:00:00Z", "제목 1", null);
        vulnerability("CVE-2026-70002", "10", "2026-05-01T00:00:00Z", "제목 2", null);
        vulnerability("CVE-2026-70003", "10", "2026-05-03T00:00:00Z", "제목 3", null);
        vulnerability("CVE-2026-70004", "10", null, "제목 4", null);

        assertThat(repository.searchEntriesSince(Instant.parse("2026-05-03T00:00:00Z")))
                .extracting(SearchListing::id)
                .containsExactly(CveId.from("CVE-2026-70001"), CveId.from("CVE-2026-70003"));
    }

    @Test
    @DisplayName("점수 상위 번호는 트렌드 순서를 따르고 철회 건을 뺀다")
    void topIdsInTrendOrder() {
        vulnerability("CVE-2026-70001", "10", "2026-10-29T00:00:00Z", null, null);
        vulnerability("CVE-2026-70002", "90", "2026-10-30T00:00:00Z", null, null);
        vulnerability("CVE-2026-70003", "95", "2026-10-30T00:00:00Z", null, null);
        jdbc.update("update vulnerability set rejected = true where vuln_id = 'CVE-2026-70003'");

        assertThat(repository.topIds(2)).containsExactly(CveId.from("CVE-2026-70002"), CveId.from("CVE-2026-70001"));
    }

    @Nested
    @DisplayName("상세는")
    class Details {

        @Test
        @DisplayName("목록 줄에 원문 설명·수정 시각·KEV 등재일·요약을 더해 읽는다")
        void rowAndExtras() {
            vulnerability("CVE-2026-41203", "74.34", "2026-10-18T00:00:00Z", "V8 타입 혼동", "Chrome");
            jdbc.update("update vulnerability set description = 'Type confusion in V8.', is_kev = true, "
                    + "kev_added_at = '2026-10-19', last_modified_at = ? where vuln_id = 'CVE-2026-41203'",
                    Instant.parse("2026-10-20T03:00:00Z").atOffset(ZoneOffset.UTC));
            jdbc.update("insert into summary (vuln_id, what, who, action, model, generated_at) values "
                    + "('CVE-2026-41203', '원격 코드 실행', 'Chrome 사용자', '업데이트', 'claude-haiku-5-5', ?)",
                    Instant.parse("2026-10-30T00:00:00Z").atOffset(ZoneOffset.UTC));

            CveDetailListing detail = only(repository.details(List.of(CveId.from("CVE-2026-41203")), NOW));

            assertThat(detail.row().title()).isEqualTo("V8 타입 혼동");
            assertThat(detail.row().breakdown().getKev()).isEqualByComparingTo("50");
            assertThat(detail.description()).isEqualTo("Type confusion in V8.");
            assertThat(detail.lastModifiedAt()).isEqualTo(Instant.parse("2026-10-20T03:00:00Z"));
            assertThat(detail.kevAddedAt()).isEqualTo(LocalDate.parse("2026-10-19"));
            assertThat(detail.summary().what()).isEqualTo("원격 코드 실행");
            assertThat(detail.summary().model()).isEqualTo("claude-haiku-5-5");
            assertThat(detail.summary().generatedAt()).isEqualTo(Instant.parse("2026-10-30T00:00:00Z"));
        }

        @Test
        @DisplayName("영향 제품은 제조사·제품 순서로, 표에 있으면 표시 이름을 붙여 읽는다")
        void affectedWithDisplayName() {
            vulnerability("CVE-2026-41203", "10", "2026-10-18T00:00:00Z", null, null);
            jdbc.update("insert into product_alias (cpe_vendor, cpe_product, slug, display_name) values "
                    + "('google', 'chrome', 'chrome', 'Chrome')");
            jdbc.update("insert into affected_product (vuln_id, vendor, product, slug, vulnerable_range) values "
                    + "('CVE-2026-41203', 'google', 'chrome', 'chrome', '< 130.0.6723.58'), "
                    + "('CVE-2026-41203', 'debian', 'debian_linux', null, null)");

            CveDetailListing detail = only(repository.details(List.of(CveId.from("CVE-2026-41203")), NOW));

            assertThat(detail.affected()).containsExactly(
                    new AffectedListing("debian", "debian_linux", null, null, null, null, null),
                    new AffectedListing("google", "chrome", "Chrome", "chrome", null, "< 130.0.6723.58", null));
        }

        @Test
        @DisplayName("참고 링크는 벤더·NVD·뉴스·PoC 순서로, 같은 출처 안에서는 주소 순으로 읽는다")
        void referencesBySource() {
            vulnerability("CVE-2026-41203", "10", "2026-10-18T00:00:00Z", null, null);
            jdbc.update("insert into reference_link (vuln_id, url, source, title) values "
                    + "('CVE-2026-41203', 'https://poc.example/1', 'poc', null), "
                    + "('CVE-2026-41203', 'https://news.example/1', 'news', '보안 뉴스'), "
                    + "('CVE-2026-41203', 'https://vendor.example/b', 'vendor', null), "
                    + "('CVE-2026-41203', 'https://vendor.example/a', 'vendor', null)");

            CveDetailListing detail = only(repository.details(List.of(CveId.from("CVE-2026-41203")), NOW));

            assertThat(detail.references()).containsExactly(
                    new ReferenceListing("https://vendor.example/a", "vendor", null),
                    new ReferenceListing("https://vendor.example/b", "vendor", null),
                    new ReferenceListing("https://news.example/1", "news", "보안 뉴스"),
                    new ReferenceListing("https://poc.example/1", "poc", null));
        }

        @Test
        @DisplayName("요약·영향 제품·참고 링크가 없으면 비워 두고 철회된 CVE 는 읽지 않는다")
        void bareAndRejected() {
            vulnerability("CVE-2026-70001", "10", "2026-10-18T00:00:00Z", null, null);
            vulnerability("CVE-2026-70002", "10", "2026-10-18T00:00:00Z", null, null);
            jdbc.update("update vulnerability set rejected = true where vuln_id = 'CVE-2026-70002'");

            List<CveDetailListing> details = repository.details(
                    List.of(CveId.from("CVE-2026-70001"), CveId.from("CVE-2026-70002")), NOW);

            CveDetailListing detail = only(details);
            assertThat(detail.row().id()).isEqualTo(CveId.from("CVE-2026-70001"));
            assertThat(detail.summary()).isNull();
            assertThat(detail.affected()).isEmpty();
            assertThat(detail.references()).isEmpty();
        }

        @Test
        @DisplayName("물은 CVE 가 없으면 아무것도 읽지 않는다")
        void nothingAsked() {
            assertThat(repository.details(List.of(), NOW)).isEmpty();
        }
    }

    private void vulnerability(String id, String score, String published, String title, String product) {
        jdbc.update("insert into vulnerability (vuln_id, trend_score, published_at, title, primary_product) "
                + "values (?, ?, ?, ?, ?)", id, Objects.isNull(score) ? null : new BigDecimal(score),
                Objects.isNull(published) ? null : Instant.parse(published).atOffset(ZoneOffset.UTC), title, product);
    }

    private static CveDetailListing only(List<CveDetailListing> details) {
        assertThat(details).hasSize(1);
        return details.get(0);
    }
}
