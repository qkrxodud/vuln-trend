package com.vulntrend.batch.domain.gate;

import com.vulntrend.batch.domain.gate.dto.ProductCount;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Gate G "미매핑 primary_product"(WBS 9장, 기획서 11장 2주 튜닝). 트렌드 상위 건의 대표 제품 중 제품 표(product_alias)에 없는
 * 이름을 많은 순으로 보여 준다 — product-alias.yml 에 더할 후보. 사람이 표를 고쳐야 하므로 있으면 확인 필요(실패 아님).
 * 대표 제품이 아예 없는 건(NVD 가 아직 CPE 를 달지 않음)은 표로 고칠 수 없어 수만 적는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductMappingCheck {

    public static final String ITEM = "미매핑 대표 제품";

    public static GateItem judge(List<ProductCount> unmapped, int withoutProduct, int top) {
        String productless = "제품 없음 " + withoutProduct + "건";
        if (unmapped.isEmpty()) {
            return GateItem.of(ITEM, GateStatus.PASS, "상위 " + top + "건의 제품이 모두 표에 있음 (" + productless + ")");
        }
        int cves = unmapped.stream().mapToInt(ProductCount::count).sum();
        List<String> evidence = unmapped.stream().map(product -> product.name() + " — " + product.count() + "건")
                .toList();
        return GateItem.of(ITEM, GateStatus.REVIEW, "상위 " + top + "건 중 표에 없는 제품 " + unmapped.size() + "개("
                + cves + "건) → product-alias.yml 후보 (" + productless + ")", evidence);
    }
}
