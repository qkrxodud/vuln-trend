package com.vulntrend.batch.domain.product;

/** 제품 이름 표준화 표를 담는 저장소(product_alias) */
public interface ProductAliasRepository {

    /** 표 전체를 주어진 내용으로 바꾼다. 원본에서 지운 줄은 남지 않는다. 적재한 줄 수를 돌려준다 */
    int replaceAll(ProductAliases aliases);
}
