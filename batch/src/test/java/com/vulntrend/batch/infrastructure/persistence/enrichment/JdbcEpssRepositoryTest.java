package com.vulntrend.batch.infrastructure.persistence.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.enrichment.EpssScore;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.math.BigDecimal;
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
@Import({PostgresTestConfig.class, JdbcEpssRepository.class})
@DisplayName("악용 확률(EPSS) 반영 — 실제 저장 구조에서")
class JdbcEpssRepositoryTest {

    private static final EpssScore LOG4SHELL =
            EpssScore.of(CveId.from("CVE-2021-44228"), new BigDecimal("0.99999"), new BigDecimal("1.0"));
    private static final EpssScore PAN_OS =
            EpssScore.of(CveId.from("CVE-2024-3400"), new BigDecimal("0.94310"), new BigDecimal("0.99912"));

    @Autowired
    private JdbcEpssRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void knownVulnerabilities() {
        jdbc.update("insert into vulnerability (vuln_id, title, is_kev) values "
                + "('CVE-2021-44228', 'Log4Shell', true), ('CVE-2024-3400', 'PAN-OS', false), "
                + "('CVE-2026-11111', 'Not scored yet', false)");
    }

    @Test
    @DisplayName("취약점 목록에 있는 CVE 번호를 모두 알려 준다")
    void listsKnownIds() {
        assertThat(repository.knownIds()).containsExactlyInAnyOrder(
                CveId.from("CVE-2021-44228"), CveId.from("CVE-2024-3400"), CveId.from("CVE-2026-11111"));
        assertThat(repository.vulnerabilityCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("악용 확률과 순위가 둘 다 오른다")
    void storesScoreAndPercentile() {
        int updated = repository.applyAll(List.of(LOG4SHELL, PAN_OS));

        assertThat(updated).isEqualTo(2);
        Map<String, Object> row = epss("CVE-2024-3400");
        assertThat((BigDecimal) row.get("epss_score")).isEqualByComparingTo("0.94310");
        assertThat((BigDecimal) row.get("epss_percentile")).isEqualByComparingTo("0.99912");
    }

    @Test
    @DisplayName("이번 파일에 없는 CVE 는 확률이 없는 채로 남는다 — 0 으로 채우지 않는다")
    void absentStaysEmpty() {
        repository.applyAll(List.of(LOG4SHELL));

        assertThat(epss("CVE-2026-11111").get("epss_score")).isNull();
    }

    @Test
    @DisplayName("다음 날 확률이 바뀌면 새 값으로 바뀐다")
    void nextDayReplaces() {
        repository.applyAll(List.of(PAN_OS));

        repository.applyAll(List.of(EpssScore.of(CveId.from("CVE-2024-3400"),
                new BigDecimal("0.95001"), new BigDecimal("0.99950"))));

        assertThat((BigDecimal) epss("CVE-2024-3400").get("epss_score")).isEqualByComparingTo("0.95001");
    }

    @Test
    @DisplayName("EPSS 반영은 실제 악용 표시나 제목 같은 다른 값을 건드리지 않는다")
    void leavesOtherValues() {
        repository.applyAll(List.of(LOG4SHELL));

        Map<String, Object> row = jdbc.queryForMap(
                "select title, is_kev from vulnerability where vuln_id = 'CVE-2021-44228'");
        assertThat(row).containsEntry("title", "Log4Shell").containsEntry("is_kev", true);
    }

    @Test
    @DisplayName("반영할 점수가 없으면 아무것도 바뀌지 않는다")
    void nothingToApply() {
        assertThat(repository.applyAll(List.of())).isZero();
    }

    private Map<String, Object> epss(String id) {
        return jdbc.queryForMap("select epss_score, epss_percentile from vulnerability where vuln_id = ?", id);
    }
}
