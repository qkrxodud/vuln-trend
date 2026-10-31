package com.vulntrend.batch.infrastructure.nvd;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CPE 제품 이름 읽기")
class CpeNameTest {

    @Test
    @DisplayName("CPE 2.3 이름에서 제조사·제품·버전을 읽는다")
    void readsVendorProductVersion() {
        CpeName name = CpeName.parse("cpe:2.3:a:apache:log4j:2.0:beta9:*:*:*:*:*:*").orElseThrow();

        assertThat(name.getVendor()).isEqualTo("apache");
        assertThat(name.getProduct()).isEqualTo("log4j");
        assertThat(name.getVersion()).contains("2.0");
        assertThat(name.getUpdate()).contains("beta9");
    }

    @Test
    @DisplayName("버전이 * 나 - 면 정해진 버전이 없는 것으로 본다")
    void wildcardVersionIsAbsent() {
        assertThat(CpeName.parse("cpe:2.3:a:apache:log4j:*:*:*:*:*:*:*:*").orElseThrow().getVersion()).isEmpty();
        assertThat(CpeName.parse("cpe:2.3:a:netapp:oncommand:-:-:*:*:*:*:*:*").orElseThrow().getVersion()).isEmpty();
    }

    @Test
    @DisplayName("이름 안에 역슬래시로 감싼 특수 문자는 원래 글자로 되돌린다")
    void unescapesSpecialCharacters() {
        CpeName name = CpeName.parse("cpe:2.3:a:netapp:solidfire_\\&_hci_storage_node:-:*:*:*:*:*:*:*").orElseThrow();
        CpeName cisco = CpeName.parse("cpe:2.3:a:cisco:data_center_network_manager:11.3\\(1\\):*:*:*:*:*:*:*")
                .orElseThrow();

        assertThat(name.getProduct()).isEqualTo("solidfire_&_hci_storage_node");
        assertThat(cisco.getVersion()).contains("11.3(1)");
    }

    @Test
    @DisplayName("이름 안에 감싼 쌍점이 있어도 칸을 잘못 나누지 않는다")
    void escapedColonStaysInField() {
        CpeName name = CpeName.parse("cpe:2.3:a:acme:time\\:keeper:1.0:*:*:*:*:*:*:*").orElseThrow();

        assertThat(name.getProduct()).isEqualTo("time:keeper");
        assertThat(name.getVersion()).contains("1.0");
    }

    @Test
    @DisplayName("CPE 2.3 모양이 아니면 읽지 않는다")
    void notCpe() {
        assertThat(CpeName.parse("cpe:/a:apache:log4j:2.0")).isEmpty();
        assertThat(CpeName.parse("cpe:2.3:a:apache")).isEmpty();
        assertThat(CpeName.parse(null)).isEmpty();
    }
}
