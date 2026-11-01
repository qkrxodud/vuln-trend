package com.vulntrend.batch.domain.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CPE 2.3 표기")
class CpeCriteriaTest {

    @Test
    @DisplayName("종류·제조사·제품·대상 소프트웨어를 읽는다")
    void readsParts() {
        CpeCriteria cpe = CpeCriteria.parse("cpe:2.3:a:wpdeveloper:essential_addons:*:*:*:*:*:wordpress:*:*")
                .orElseThrow();

        assertThat(cpe.getPart()).isEqualTo("a");
        assertThat(cpe.getVendor()).isEqualTo("wpdeveloper");
        assertThat(cpe.getProduct()).isEqualTo("essential_addons");
        assertThat(cpe.getTargetSoftware()).isEqualTo("wordpress");
    }

    @Test
    @DisplayName("이름 안의 쌍점은 원래 표기대로 이스케이프된 채 한 칸으로 읽는다")
    void escapedColonStaysInName() {
        CpeCriteria cpe = CpeCriteria.parse("cpe:2.3:a:scp\\:sl:containment_breach:*:*:*:*:*:*:*:*").orElseThrow();

        assertThat(cpe.getVendor()).isEqualTo("scp\\:sl");
        assertThat(cpe.getProduct()).isEqualTo("containment_breach");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"cpe:/a:google:chrome", "cpe:2.3:a:google"})
    @DisplayName("CPE 2.3 모양이 아니면 읽지 않는다")
    void notCpe(String text) {
        assertThat(CpeCriteria.parse(text)).isEmpty();
    }
}
