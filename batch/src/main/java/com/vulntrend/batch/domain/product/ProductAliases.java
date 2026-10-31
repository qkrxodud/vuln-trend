package com.vulntrend.batch.domain.product;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;

/**
 * 제품 이름 표준화 표 전체. 맞춰 적은 열쇠 하나에 한 줄만 둔다.
 * CPE 와 KEV 이름이 맞춰 적어 같아지면(예: "PHP"/"PHP" 와 php/php) 한 줄로 합치고,
 * 같은 열쇠를 다른 제품에 잇거나 한 제품을 두 가지 이름으로 보이게 하면 표 전체를 받지 않는다 — 기동 시 바로 드러나게.
 */
public class ProductAliases {

    private final Map<ProductKey, ProductAlias> byKey;

    @Builder(access = AccessLevel.PRIVATE)
    private ProductAliases(Map<ProductKey, ProductAlias> byKey) {
        this.byKey = byKey;
    }

    public static ProductAliases of(Collection<ProductAlias> aliases) {
        Map<ProductKey, ProductAlias> byKey = new LinkedHashMap<>();
        Map<String, ProductAlias> bySlug = new LinkedHashMap<>();
        for (ProductAlias alias : aliases) {
            requireSameProduct(byKey.putIfAbsent(alias.getKey(), alias), alias, alias.getKey().toString());
            requireSameProduct(bySlug.putIfAbsent(alias.getSlug(), alias), alias, alias.getSlug());
        }
        return ProductAliases.builder().byKey(Map.copyOf(byKey)).build();
    }

    private static void requireSameProduct(ProductAlias earlier, ProductAlias alias, String shared) {
        if (Objects.isNull(earlier)) {
            return;
        }
        if (alias.isNotSameProductAs(earlier)) {
            throw new DomainValidationException("Product alias entries disagree about " + shared + ": "
                    + earlier.getSlug() + " (" + earlier.getDisplayName() + ") vs "
                    + alias.getSlug() + " (" + alias.getDisplayName() + ").");
        }
    }

    public Optional<ProductAlias> find(ProductKey key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public List<ProductAlias> getAliases() {
        return List.copyOf(byKey.values());
    }

    public int size() {
        return byKey.size();
    }

    public long productCount() {
        return byKey.values().stream().map(ProductAlias::getSlug).distinct().count();
    }
}
