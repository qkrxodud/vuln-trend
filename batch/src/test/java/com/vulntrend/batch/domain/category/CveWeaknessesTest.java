package com.vulntrend.batch.domain.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CVE 하나의 약점과 지금 공격 유형")
class CveWeaknessesTest {

    private static final CveId CVE = CveId.from("CVE-2026-0001");

    @Test
    @DisplayName("저장된 키가 같은 순서로 같으면 이미 그 유형이다")
    void sameKeys() {
        CveWeaknesses weaknesses = CveWeaknesses.of(CVE, List.of("CWE-89", "CWE-79"), List.of("injection", "xss"));

        assertThat(weaknesses.hasAttackTypes(List.of(AttackType.INJECTION, AttackType.XSS))).isTrue();
        assertThat(weaknesses.hasAttackTypes(List.of(AttackType.XSS))).isFalse();
    }

    @Test
    @DisplayName("빈 유형과 빈 키는 같다")
    void bothEmpty() {
        assertThat(CveWeaknesses.of(CVE, List.of(), List.of()).hasAttackTypes(List.of())).isTrue();
    }

    @Test
    @DisplayName("약점 번호로 매긴 유형을 낸다")
    void classifies() {
        assertThat(CveWeaknesses.of(CVE, List.of("CWE-79"), List.of()).classify()).containsExactly(AttackType.XSS);
    }
}
