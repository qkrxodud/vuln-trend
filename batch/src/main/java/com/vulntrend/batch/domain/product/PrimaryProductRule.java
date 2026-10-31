package com.vulntrend.batch.domain.product;

import java.util.Optional;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.Builder;

/**
 * 대표 제품(primary_product)을 정하는 규칙(기획서 5장 메모 P11, WBS 2.3). 순서:
 * 1) KEV 표기가 표에 있으면 그 표준 이름  2) CPE 첫 항목이 표에 있으면 그 표준 이름
 * 3) CPE 첫 항목의 제품 이름 그대로  4) NVD 가 아직 CPE 를 달지 않았으면 KEV 제품 이름 그대로  5) 없음
 * KEV 의 "여러 제품" 묶음 표기는 한 제품을 가리키지 않으므로 순위에서 뺀다. OSV(2단계)는 1과 2 사이에 들어갈 자리다.
 */
public class PrimaryProductRule {

    /** vulnerability.primary_product 는 varchar(100) */
    private static final int MAX_LENGTH = 100;

    private final ProductAliases aliases;

    @Builder(access = AccessLevel.PRIVATE)
    private PrimaryProductRule(ProductAliases aliases) {
        this.aliases = aliases;
    }

    public static PrimaryProductRule of(ProductAliases aliases) {
        return PrimaryProductRule.builder().aliases(aliases).build();
    }

    public Optional<String> choose(Optional<ProductMention> kev, Optional<ProductMention> firstCpe) {
        Optional<ProductMention> singleKev = kev.filter(Predicate.not(ProductMention::isGeneric));
        return singleKev.flatMap(this::standardName)
                .or(() -> firstCpe.flatMap(this::standardName))
                .or(() -> firstCpe.map(ProductMention::getProduct))
                .or(() -> singleKev.map(ProductMention::getProduct))
                .map(PrimaryProductRule::fitToStorage);
    }

    public Optional<String> slugOf(ProductMention mention) {
        return find(mention).map(ProductAlias::getSlug);
    }

    private Optional<String> standardName(ProductMention mention) {
        return find(mention).map(ProductAlias::getDisplayName);
    }

    private Optional<ProductAlias> find(ProductMention mention) {
        return mention.key().flatMap(aliases::find);
    }

    private static String fitToStorage(String name) {
        return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name;
    }
}
