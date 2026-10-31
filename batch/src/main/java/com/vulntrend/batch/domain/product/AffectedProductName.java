package com.vulntrend.batch.domain.product;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 영향받는 제품 목록에 나오는 제조사·제품과 지금 붙은 묶음 이름 하나. 같은 제품이 묶음 이름별로 따로 나올 수 있다 —
 * 새로 들어온 줄은 묶음 이름이 비어 있고 예전 줄은 붙어 있기 때문이다. 그래서 하나라도 다르면 그 제품을 다시 맞춘다.
 */
public class AffectedProductName {

    @Getter
    private final ProductMention mention;
    private final String currentSlug;

    @Builder(access = AccessLevel.PRIVATE)
    private AffectedProductName(ProductMention mention, String currentSlug) {
        this.mention = mention;
        this.currentSlug = currentSlug;
    }

    public static AffectedProductName of(ProductMention mention, String currentSlug) {
        return AffectedProductName.builder().mention(mention).currentSlug(currentSlug).build();
    }

    public Optional<String> getCurrentSlug() {
        return Optional.ofNullable(currentSlug);
    }
}
