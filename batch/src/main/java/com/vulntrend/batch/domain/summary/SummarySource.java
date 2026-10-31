package com.vulntrend.batch.domain.summary;

import com.vulntrend.batch.domain.vulnerability.AffectedProduct;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 요약에 넘기는 원문. NVD 설명과 영향받는 제품·버전 범위만 담는다(기획서 6장 요약 가드) */
@Getter
public class SummarySource {

    private final String description;
    private final List<AffectedProduct> products;

    @Builder(access = AccessLevel.PRIVATE)
    private SummarySource(String description, List<AffectedProduct> products) {
        this.description = description;
        this.products = products;
    }

    public static SummarySource of(String description, List<AffectedProduct> products) {
        return SummarySource.builder().description(description).products(List.copyOf(products)).build();
    }
}
