package com.vulntrend.batch.infrastructure.persistence.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.product.AffectedProductName;
import com.vulntrend.batch.domain.product.CpeCriteria;
import com.vulntrend.batch.domain.product.PrimaryProductCandidate;
import com.vulntrend.batch.domain.product.ProductFamily;
import com.vulntrend.batch.domain.product.ProductMention;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@Import({PostgresTestConfig.class, JdbcProductAssignmentRepository.class})
@DisplayName("대표 제품과 묶음 이름 — 실제 저장 구조에서")
class JdbcProductAssignmentRepositoryTest {

    /** 첫 설정의 첫 항목은 영향받지 않는 실행 환경(windows)이고, 영향받는 첫 항목은 chrome 이다 */
    private static final String CHROME_ON_WINDOWS = """
            {"id":"CVE-2026-10001","sourceIdentifier":"chrome-cve-admin@google.com","configurations":[
              {"nodes":[{"operator":"OR","cpeMatch":[
                {"vulnerable":false,"criteria":"cpe:2.3:o:microsoft:windows:-:*:*:*:*:*:*:*"},
                {"vulnerable":true,"criteria":"cpe:2.3:a:google:chrome:*:*:*:*:*:*:*:*"}]}]},
              {"nodes":[{"operator":"OR","cpeMatch":[
                {"vulnerable":true,"criteria":"cpe:2.3:a:microsoft:edge_chromium:*:*:*:*:*:*:*:*"}]}]}]}
            """;
    private static final String ESCAPED_CPE = """
            {"id":"CVE-2026-10002","configurations":[{"nodes":[{"cpeMatch":[
              {"vulnerable":true,"criteria":"cpe:2.3:a:intel:proset\\\\/wireless_wifi:*:*:*:*:*:*:*:*"}]}]}]}
            """;
    private static final String AWAITING_ANALYSIS = """
            {"id":"CVE-2026-10003","vulnStatus":"Awaiting Analysis"}
            """;
    private static final String LOG4SHELL_KEV = """
            {"cveID":"CVE-2021-44228","vendorProject":"Apache","product":"Log4j2"}
            """;

    @Autowired
    private JdbcProductAssignmentRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void vulnerabilities() {
        jdbc.update("insert into vulnerability (vuln_id, primary_product) values "
                + "('CVE-2026-10001', null), ('CVE-2026-10002', null), ('CVE-2026-10003', 'Old name'), "
                + "('CVE-2021-44228', null)");
        raw("CVE-2026-10001", "nvd", CHROME_ON_WINDOWS);
        raw("CVE-2026-10002", "nvd", ESCAPED_CPE);
        raw("CVE-2026-10003", "nvd", AWAITING_ANALYSIS);
        raw("CVE-2021-44228", "kev", LOG4SHELL_KEV);
    }

    @Nested
    @DisplayName("대표 제품 후보를 모으면")
    class Candidates {

        @Test
        @DisplayName("NVD 원본에서 영향받는 첫 CPE 항목을 고른다 — 실행 환경 항목은 건너뛴다")
        void firstVulnerableCpe() {
            PrimaryProductCandidate chrome = candidate("CVE-2026-10001");

            assertThat(chrome.getFirstCpe()).map(ProductMention::getProduct).contains("chrome");
            assertThat(chrome.getKev()).isEmpty();
        }

        @Test
        @DisplayName("CPE 의 escape 된 글자는 원래 글자로 읽는다")
        void escapedCpeIsRead() {
            assertThat(candidate("CVE-2026-10002").getFirstCpe()).map(ProductMention::getProduct)
                    .contains("proset/wireless_wifi");
        }

        @Test
        @DisplayName("KEV 원본의 제조사·제품 표기를 함께 가져온다")
        void kevNameIsRead() {
            PrimaryProductCandidate log4shell = candidate("CVE-2021-44228");

            assertThat(log4shell.getKev()).map(ProductMention::getProduct).contains("Log4j2");
            assertThat(log4shell.getFirstCpe()).isEmpty();
        }

        @Test
        @DisplayName("아직 CPE 가 없는 CVE 는 CPE 후보 없이 지금 대표 제품과 함께 나온다")
        void noCpeYet() {
            PrimaryProductCandidate waiting = candidate("CVE-2026-10003");

            assertThat(waiting.getFirstCpe()).isEmpty();
            assertThat(waiting.getCurrentPrimary()).contains("Old name");
        }
    }

    @Test
    @DisplayName("제품군 근거로 영향받는 첫 CPE 원문과 NVD 발행 기관, 지금 제품군을 함께 가져온다")
    void familyEvidence() {
        jdbc.update("update vulnerability set product_family = 'browser' where vuln_id = 'CVE-2026-10001'");

        PrimaryProductCandidate chrome = candidate("CVE-2026-10001");

        assertThat(chrome.getFirstCpeCriteria()).map(CpeCriteria::getProduct).contains("chrome");
        assertThat(chrome.getCna()).isEqualTo("chrome-cve-admin@google.com");
        assertThat(chrome.getCurrentFamily()).contains(ProductFamily.BROWSER);
        assertThat(candidate("CVE-2026-10003").getFirstCpeCriteria()).isEmpty();
        assertThat(candidate("CVE-2026-10003").getCurrentFamily()).isEmpty();
    }

    @Test
    @DisplayName("제품군을 바꾸거나 분류 전으로 비울 수 있다")
    void changesFamily() {
        jdbc.update("update vulnerability set product_family = 'desktop' where vuln_id = 'CVE-2026-10003'");

        int changed = repository.changeFamilies(Map.of(
                CveId.from("CVE-2026-10001"), Optional.of(ProductFamily.BROWSER),
                CveId.from("CVE-2026-10003"), Optional.empty()));

        assertThat(changed).isEqualTo(2);
        assertThat(family("CVE-2026-10001")).isEqualTo("browser");
        assertThat(family("CVE-2026-10003")).isNull();
    }

    @Test
    @DisplayName("대표 제품을 바꾸거나 비울 수 있다")
    void changesPrimary() {
        int changed = repository.changePrimaryProducts(Map.of(
                CveId.from("CVE-2026-10001"), Optional.of("Chrome"),
                CveId.from("CVE-2026-10003"), Optional.empty()));

        assertThat(changed).isEqualTo(2);
        assertThat(primary("CVE-2026-10001")).isEqualTo("Chrome");
        assertThat(primary("CVE-2026-10003")).isNull();
    }

    @Nested
    @DisplayName("영향받는 제품의 묶음 이름은")
    class Slugs {

        @BeforeEach
        void affected() {
            jdbc.update("insert into affected_product (vuln_id, vendor, product, slug) values "
                    + "('CVE-2026-10001', 'google', 'chrome', null), ('CVE-2026-10002', 'google', 'chrome', null), "
                    + "('CVE-2026-10001', 'gimp', 'gimp', 'gimp-old')");
        }

        @Test
        @DisplayName("같은 제조사·제품은 CVE 가 여럿이어도 한 번만 나온다")
        void distinctNames() {
            List<AffectedProductName> names = repository.findAffectedProductNames();

            assertThat(names).extracting(name -> name.getMention().getProduct())
                    .containsExactlyInAnyOrder("chrome", "gimp");
        }

        @Test
        @DisplayName("묶음 이름을 바꾸면 그 제품이 나오는 모든 CVE 에 반영되고, 비울 수도 있다")
        void changesEverywhere() {
            int changed = repository.changeSlugs(Map.of(
                    ProductMention.from("google", "chrome").orElseThrow(), Optional.of("chrome"),
                    ProductMention.from("gimp", "gimp").orElseThrow(), Optional.empty()));

            assertThat(changed).isEqualTo(3);
            assertThat(jdbc.queryForList("select slug from affected_product where product = 'chrome'", String.class))
                    .containsExactly("chrome", "chrome");
            assertThat(jdbc.queryForObject("select slug from affected_product where product = 'gimp'", String.class))
                    .isNull();
        }
    }

    private PrimaryProductCandidate candidate(String id) {
        return repository.findPrimaryCandidates().stream()
                .filter(candidate -> candidate.getCveId().getValue().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private String family(String id) {
        return jdbc.queryForObject("select product_family from vulnerability where vuln_id = ?", String.class, id);
    }

    private String primary(String id) {
        return jdbc.queryForObject("select primary_product from vulnerability where vuln_id = ?", String.class, id);
    }

    private void raw(String id, String source, String payload) {
        jdbc.update("insert into vulnerability_raw (vuln_id, source, payload, fetched_at) "
                + "values (?, ?, cast(? as jsonb), now())", id, source, payload);
    }
}
