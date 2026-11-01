package com.vulntrend.batch.infrastructure.persistence.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** product-alias.yml 의 제품 하나. cpe·kev 는 원래 이름 목록이고 둘 중 하나는 비어 있을 수 있다. family 는 제품군 키(비울 수 있다) */
public record ProductAliasEntry(
        String slug,
        @JsonProperty("display_name") String displayName,
        String family,
        List<ProductAliasName> cpe,
        List<ProductAliasName> kev) {
}
