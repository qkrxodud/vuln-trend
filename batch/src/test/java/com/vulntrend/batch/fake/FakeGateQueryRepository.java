package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.gate.GateQueryRepository;
import com.vulntrend.batch.domain.gate.dto.ProductCount;
import java.util.ArrayList;
import java.util.List;

/** Gate G 집계 흉내. 넣어 둔 미매핑 제품과 제품 없는 수를 돌려주고, 상위 몇 건을 물었는지 기억한다 */
public class FakeGateQueryRepository implements GateQueryRepository {

    private final List<ProductCount> unmapped = new ArrayList<>();
    private int withoutProduct;
    private int askedTop;

    public FakeGateQueryRepository unmapped(String vendor, String name, int count) {
        unmapped.add(new ProductCount(vendor, name, count));
        return this;
    }

    public FakeGateQueryRepository productless(int count) {
        this.withoutProduct = count;
        return this;
    }

    @Override
    public List<ProductCount> unmappedProducts(int top) {
        this.askedTop = top;
        return List.copyOf(unmapped);
    }

    @Override
    public int withoutProduct(int top) {
        return withoutProduct;
    }

    public int askedTop() {
        return askedTop;
    }
}
