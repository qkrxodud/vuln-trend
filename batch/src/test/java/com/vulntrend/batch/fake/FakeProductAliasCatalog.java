package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.product.ProductAliasCatalog;
import com.vulntrend.batch.domain.product.ProductAliases;

/** 제품 이름 표준화 표 원본 흉내. 정해 둔 표를 돌려준다 */
public class FakeProductAliasCatalog implements ProductAliasCatalog {

    private final ProductAliases aliases;

    public FakeProductAliasCatalog(ProductAliases aliases) {
        this.aliases = aliases;
    }

    @Override
    public ProductAliases load() {
        return aliases;
    }
}
