package com.vulntrend.batch.domain.product;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 원래 적힌 제품 이름(KEV vendorProject/product, CPE vendor/product). 표에 없을 때 사이트에 그대로 보이므로 적힌 대로 담는다.
 * 표 찾기에는 맞춰 적은 열쇠(ProductKey)를 쓴다. 맞춰 적은 이름이 100자를 넘으면 표에 있을 수 없어 열쇠가 없다.
 */
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProductMention {

    /** KEV 의 "Multiple Products", "Multiple Firewalls", "MobileIron Multiple Products" 같은 묶음 표기 */
    private static final Pattern GENERIC = Pattern.compile("(^|_)multiple(_|$)");

    @Getter
    @EqualsAndHashCode.Include
    private final String vendor;
    @Getter
    @EqualsAndHashCode.Include
    private final String product;
    private final ProductKey key;

    @Builder(access = AccessLevel.PRIVATE)
    private ProductMention(String vendor, String product, ProductKey key) {
        this.vendor = vendor;
        this.product = product;
        this.key = key;
    }

    /** 제조사나 제품 이름에 영문·숫자가 하나도 없으면 이름이 없는 것으로 본다 */
    public static Optional<ProductMention> from(String vendor, String product) {
        if (hasNoName(vendor) || hasNoName(product)) {
            return Optional.empty();
        }
        return Optional.of(ProductMention.builder()
                .vendor(vendor.trim())
                .product(product.trim())
                .key(keyOrNothing(vendor, product))
                .build());
    }

    public Optional<ProductKey> key() {
        return Optional.ofNullable(key);
    }

    public boolean isGeneric() {
        return GENERIC.matcher(ProductKey.normalize(product)).find();
    }

    private static boolean hasNoName(String name) {
        return Objects.isNull(name) || ProductKey.normalize(name).isEmpty();
    }

    private static ProductKey keyOrNothing(String vendor, String product) {
        try {
            return ProductKey.of(vendor, product);
        } catch (DomainValidationException tooLong) {
            return null;
        }
    }
}
