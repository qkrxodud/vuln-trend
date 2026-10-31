package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.product.ProductAliasRepository;
import com.vulntrend.batch.domain.product.ProductAliases;
import java.util.List;
import java.util.Optional;

/** 제품 이름 표준화 표 저장소 흉내(메모리). 마지막으로 바꿔 넣은 표를 기억한다 */
public class FakeProductAliasRepository implements ProductAliasRepository {

    private ProductAliases stored;

    @Override
    public int replaceAll(ProductAliases aliases) {
        this.stored = aliases;
        return aliases.size();
    }

    public Optional<ProductAliases> stored() {
        return Optional.ofNullable(stored);
    }

    public List<String> storedSlugs() {
        return stored().map(aliases -> aliases.getAliases().stream().map(a -> a.getSlug()).distinct().toList())
                .orElseGet(List::of);
    }
}
