package com.vulntrend.batch.infrastructure.persistence.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.category.AttackType;
import com.vulntrend.batch.domain.category.CveWeaknesses;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcAttackTypeAssignmentRepository.class})
@DisplayName("공격 유형 매기기 — 실제 저장 구조에서")
class JdbcAttackTypeAssignmentRepositoryTest {

    private static final CveId LOG4SHELL = CveId.from("CVE-2021-44228");
    private static final CveId CHROME = CveId.from("CVE-2026-41203");
    private static final CveId KEV_ONLY = CveId.from("CVE-2026-0001");

    @Autowired
    private JdbcAttackTypeAssignmentRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("NVD 원본의 약점 번호를 모든 CVE 에 대해 읽고, 처음에는 저장된 유형이 비어 있다")
    void readsWeaknessesOfEveryCve() {
        raw(LOG4SHELL, "nvd", """
                {"weaknesses": [
                  {"source": "nvd@nist.gov", "description": [{"lang": "en", "value": "CWE-917"}]},
                  {"source": "security@apache.org",
                   "description": [{"lang": "en", "value": "CWE-502"}, {"lang": "en", "value": "CWE-20"}]}]}""");
        raw(CHROME, "nvd", "{\"weaknesses\": [{\"description\": [{\"value\": \"NVD-CWE-noinfo\"}]}]}");
        raw(KEV_ONLY, "kev", "{\"weaknesses\": [{\"description\": [{\"value\": \"CWE-79\"}]}]}");

        Map<CveId, CveWeaknesses> found = byId(repository.findWeaknesses());

        assertThat(found).containsOnlyKeys(LOG4SHELL, CHROME, KEV_ONLY);
        assertThat(found.get(LOG4SHELL).getCweIds()).containsExactlyInAnyOrder("CWE-917", "CWE-502", "CWE-20");
        assertThat(found.get(CHROME).getCweIds()).containsExactly("NVD-CWE-noinfo");
        assertThat(found.get(KEV_ONLY).getCweIds()).isEmpty();
        assertThat(found.get(LOG4SHELL).getCurrentKeys()).isEmpty();
    }

    @Test
    @DisplayName("약점 칸의 모양이 배열이 아니면 약점이 없는 것으로 읽는다")
    void oddShapeIsNoWeakness() {
        raw(CHROME, "nvd", "{\"weaknesses\": {\"description\": \"CWE-79\"}}");
        raw(LOG4SHELL, "nvd", "{\"weaknesses\": [{\"description\": \"CWE-79\"}, {\"description\": [{}]}]}");

        Map<CveId, CveWeaknesses> found = byId(repository.findWeaknesses());

        assertThat(found.get(CHROME).getCweIds()).isEmpty();
        assertThat(found.get(LOG4SHELL).getCweIds()).isEmpty();
    }

    @Test
    @DisplayName("바꾼 공격 유형 키가 순서대로 남고, 다시 읽으면 지금 유형으로 나온다")
    void changesAreStored() {
        raw(LOG4SHELL, "nvd", "{}");
        raw(CHROME, "nvd", "{}");

        int changed = repository.changeAttackTypes(Map.of(LOG4SHELL, List.of(AttackType.RCE, AttackType.DOS),
                CHROME, List.of()));

        assertThat(changed).isEqualTo(2);
        assertThat(byId(repository.findWeaknesses()).get(LOG4SHELL).getCurrentKeys()).containsExactly("rce", "dos");
        assertThat(byId(repository.findWeaknesses()).get(CHROME).getCurrentKeys()).isEmpty();
    }

    @Test
    @DisplayName("바꿀 것이 없으면 저장소에 쓰지 않는다")
    void nothingToChange() {
        assertThat(repository.changeAttackTypes(Map.of())).isZero();
    }

    private static Map<CveId, CveWeaknesses> byId(List<CveWeaknesses> list) {
        return list.stream().collect(Collectors.toMap(CveWeaknesses::getCveId, weaknesses -> weaknesses));
    }

    private void raw(CveId id, String source, String payload) {
        jdbc.update("insert into vulnerability (vuln_id) values (?) on conflict do nothing", id.getValue());
        jdbc.update("insert into vulnerability_raw (vuln_id, source, payload) values (?, ?, cast(? as jsonb))",
                id.getValue(), source, payload);
    }
}
