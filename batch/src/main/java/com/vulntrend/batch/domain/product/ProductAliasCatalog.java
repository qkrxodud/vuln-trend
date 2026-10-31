package com.vulntrend.batch.domain.product;

/** 제품 이름 표준화 표의 원본(저장소 안의 product-alias.yml). 사람이 고치는 단일 출처다 */
public interface ProductAliasCatalog {

    ProductAliases load();
}
