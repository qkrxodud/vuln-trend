package com.vulntrend.batch.infrastructure.persistence.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.enrichment.KevEntry;
import com.vulntrend.batch.domain.enrichment.KevMarking;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
@Import({PostgresTestConfig.class, JdbcKevRepository.class})
@DisplayName("실제 악용 목록(KEV) 반영 — 실제 저장 구조에서")
class JdbcKevRepositoryTest {

    private static final Instant FETCHED = Instant.parse("2026-10-31T00:00:00Z");
    private static final KevEntry LOG4SHELL = KevEntry.of(CveId.from("CVE-2021-44228"),
            LocalDate.of(2021, 12, 10), LocalDate.of(2021, 12, 24), "Known",
            "{\"cveID\":\"CVE-2021-44228\",\"vendorProject\":\"Apache\",\"product\":\"Log4j2\"}");
    private static final KevEntry GITLAB = KevEntry.of(CveId.from("CVE-2026-97229"),
            LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 1), "Unknown",
            "{\"cveID\":\"CVE-2026-97229\",\"vendorProject\":\"GitLab\"}");
    private static final KevEntry NOT_YET_IN_NVD = KevEntry.of(CveId.from("CVE-2026-55555"),
            LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 20), "Unknown",
            "{\"cveID\":\"CVE-2026-55555\"}");

    @Autowired
    private JdbcKevRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void knownVulnerabilities() {
        jdbc.update("insert into vulnerability (vuln_id, title, epss_score) values "
                + "('CVE-2021-44228', 'Log4Shell', 0.97), ('CVE-2026-97229', 'GitLab', null), "
                + "('CVE-2026-11111', 'Not exploited', null)");
    }

    @Test
    @DisplayName("목록에 있는 CVE 에 실제 악용 표시와 등재일·조치 기한·랜섬웨어 사용 여부가 붙는다")
    void marksListedCves() {
        KevMarking marking = repository.markAll(List.of(LOG4SHELL, GITLAB), FETCHED);

        assertThat(marking.getMarked()).isEqualTo(2);
        assertThat(kevColumns("CVE-2021-44228")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "is_kev", true, "kev_added_at", Date.valueOf("2021-12-10"),
                "kev_due_date", Date.valueOf("2021-12-24"), "kev_ransomware", true));
        assertThat(kevColumns("CVE-2026-97229")).containsEntry("is_kev", true).containsEntry("kev_ransomware", false);
    }

    @Test
    @DisplayName("목록에 없는 CVE 는 실제 악용 표시가 붙지 않는다")
    void unlistedStaysUnmarked() {
        repository.markAll(List.of(LOG4SHELL), FETCHED);

        assertThat(kevColumns("CVE-2026-11111")).containsEntry("is_kev", false);
    }

    @Test
    @DisplayName("아직 취약점 목록에 없는 CVE 는 표시하지 못한 것으로 알려 주고 NVD 다음 수집을 기다린다")
    void reportsCvesNotYetCollected() {
        KevMarking marking = repository.markAll(List.of(LOG4SHELL, NOT_YET_IN_NVD), FETCHED);

        assertThat(marking.getMarked()).isEqualTo(1);
        assertThat(marking.getMissing()).containsExactly(CveId.from("CVE-2026-55555"));
        assertThat(jdbc.queryForObject("select count(*) from vulnerability where vuln_id = 'CVE-2026-55555'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("KEV 원본은 취약점 목록에 있든 없든 한 건씩 그대로 보관한다")
    void keepsRawForEveryEntry() {
        repository.markAll(List.of(LOG4SHELL, NOT_YET_IN_NVD), FETCHED);

        List<Map<String, Object>> raw = jdbc.queryForList(
                "select vuln_id, payload ->> 'vendorProject' as vendor from vulnerability_raw where source = 'kev' "
                        + "order by vuln_id");
        assertThat(raw).extracting(row -> row.get("vuln_id")).containsExactly("CVE-2021-44228", "CVE-2026-55555");
        assertThat(raw.get(0).get("vendor")).isEqualTo("Apache");
    }

    @Test
    @DisplayName("CISA 가 목록에서 내린 CVE 는 실제 악용 표시가 지워진다")
    void delistedCveIsUnmarked() {
        repository.markAll(List.of(LOG4SHELL, GITLAB), FETCHED);

        repository.markAll(List.of(LOG4SHELL), FETCHED.plusSeconds(86_400));

        assertThat(kevColumns("CVE-2026-97229")).containsEntry("is_kev", false)
                .containsEntry("kev_added_at", null).containsEntry("kev_due_date", null)
                .containsEntry("kev_ransomware", false);
        assertThat(kevColumns("CVE-2021-44228")).containsEntry("is_kev", true);
    }

    @Test
    @DisplayName("같은 목록을 두 번 반영해도 표시와 보관 원본이 두 번 오르지 않는다")
    void sameCatalogTwiceIsStable() {
        repository.markAll(List.of(LOG4SHELL, GITLAB), FETCHED);
        repository.markAll(List.of(LOG4SHELL, GITLAB), FETCHED);

        assertThat(jdbc.queryForObject("select count(*) from vulnerability where is_kev", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from vulnerability_raw where source = 'kev'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("KEV 반영은 제목이나 악용 확률 같은 다른 값을 건드리지 않는다")
    void leavesOtherValues() {
        repository.markAll(List.of(LOG4SHELL), FETCHED);

        Map<String, Object> row = jdbc.queryForMap(
                "select title, epss_score from vulnerability where vuln_id = 'CVE-2021-44228'");
        assertThat(row.get("title")).isEqualTo("Log4Shell");
        assertThat((BigDecimal) row.get("epss_score")).isEqualByComparingTo("0.97");
    }

    private Map<String, Object> kevColumns(String id) {
        return jdbc.queryForMap(
                "select is_kev, kev_added_at, kev_due_date, kev_ransomware from vulnerability where vuln_id = ?", id);
    }
}
