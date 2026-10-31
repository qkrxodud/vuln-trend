package com.vulntrend.batch.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.gate.dto.ProductCount;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gate G — 제품 표에 없는 대표 제품")
class ProductMappingCheckTest {

    @Test
    @DisplayName("상위 목록의 제품이 모두 표에 있으면 통과다")
    void allMapped() {
        GateItem item = ProductMappingCheck.judge(List.of(), 4, 100);

        assertThat(item.getStatus()).isEqualTo(GateStatus.PASS);
        assertThat(item.getDetail()).contains("상위 100건", "제품 없음 4건");
    }

    @Test
    @DisplayName("표에 없는 제품이 있으면 많은 순서대로 product-alias.yml 후보로 보여 주고 확인 필요다")
    void unmappedAreCandidates() {
        GateItem item = ProductMappingCheck.judge(
                List.of(new ProductCount("connect_secure", 4), new ProductCount("sharepoint_server", 2)), 0, 100);

        assertThat(item.getStatus()).isEqualTo(GateStatus.REVIEW);
        assertThat(item.getDetail()).contains("2개", "6건");
        assertThat(item.getEvidence()).containsExactly("connect_secure — 4건", "sharepoint_server — 2건");
    }
}
