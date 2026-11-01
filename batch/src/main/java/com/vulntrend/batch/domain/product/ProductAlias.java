package com.vulntrend.batch.domain.product;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 제품 이름 표준화 표의 한 줄(product_alias 한 행). 원래 이름(CPE 또는 KEV)을 묶음 이름(slug)과 사이트에 보일 이름에 잇는다.
 * display_name 이 vulnerability.primary_product 에, slug 가 affected_product.slug 에 들어간다(기획서 5장 메모 P11).
 * family 는 제품군(사용자 결정 2026-11-01). 같은 제품의 줄은 제품군도 같아야 한다.
 */
@Getter
@EqualsAndHashCode
public class ProductAlias {

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MAX_LENGTH = 100;

    private final ProductKey key;
    private final String slug;
    private final String displayName;
    @Getter(AccessLevel.NONE)
    private final ProductFamily family;

    @Builder(access = AccessLevel.PRIVATE)
    private ProductAlias(ProductKey key, String slug, String displayName, ProductFamily family) {
        validate(key, slug, displayName);
        this.key = key;
        this.slug = slug;
        this.displayName = displayName;
        this.family = family;
    }

    /** 제품군을 정하지 않은 줄(애매하면 비워 둔다) */
    public static ProductAlias of(ProductKey key, String slug, String displayName) {
        return of(key, slug, displayName, null);
    }

    public static ProductAlias of(ProductKey key, String slug, String displayName, ProductFamily family) {
        return ProductAlias.builder()
                .key(key)
                .slug(slug)
                .displayName(Objects.isNull(displayName) ? null : displayName.trim())
                .family(family)
                .build();
    }

    /** 제품군. 표에서 비워 둔 제품은 없다 */
    public Optional<ProductFamily> getFamily() {
        return Optional.ofNullable(family);
    }

    /** 같은 원래 이름을 다른 제품(묶음 이름이나 보일 이름이 다름)에 잇고 있는가 */
    public boolean isNotSameProductAs(ProductAlias other) {
        return isNotSameText(slug, other.slug) || isNotSameText(displayName, other.displayName)
                || Predicate.not(getFamily()::equals).test(other.getFamily());
    }

    private static boolean isNotSameText(String one, String other) {
        return Predicate.not(one::equals).test(other);
    }

    private static void validate(ProductKey key, String slug, String displayName) {
        if (Objects.isNull(key)) {
            throw new DomainValidationException("A product alias needs the original vendor and product name.");
        }
        if (isNotSlug(slug)) {
            throw new DomainValidationException("A product slug may use lowercase letters, digits and inner hyphens only.");
        }
        if (Objects.isNull(displayName) || displayName.isEmpty()) {
            throw new DomainValidationException("A product needs a name to show on the site.");
        }
        if (displayName.length() > MAX_LENGTH) {
            throw new DomainValidationException("A product display name must be at most " + MAX_LENGTH + " characters.");
        }
    }

    private static boolean isNotSlug(String slug) {
        return Objects.isNull(slug) || slug.length() > MAX_LENGTH || Predicate.not(SLUG.asMatchPredicate()).test(slug);
    }
}
