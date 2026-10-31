package com.vulntrend.batch.domain.gate;

import com.vulntrend.batch.domain.gate.dto.ProductCount;
import java.util.List;

/** Gate G 집계 조회 */
public interface GateQueryRepository {

    /** 트렌드 순서 상위 top 건 중 제품 표(product_alias)에 없는 대표 제품 이름, 많은 순 → 이름 순 */
    List<ProductCount> unmappedProducts(int top);

    /** 트렌드 순서 상위 top 건 중 대표 제품이 아예 없는 수 */
    int withoutProduct(int top);
}
