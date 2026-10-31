package com.vulntrend.batch.infrastructure.persistence.product.dto;

import java.util.List;

/** product-alias.yml 의 모양. 최상위 키 products 아래에 제품을 늘어놓는다 */
public record ProductAliasFile(List<ProductAliasEntry> products) {
}
