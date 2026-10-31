package com.vulntrend.batch.application.product;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 이번 실행의 대표 제품·묶음 이름 매기기 결과. 바뀐 CVE 수와 바뀐 영향받는 제품 줄 수 */
@Getter
public class ProductAssignment {

    private final int primaryChanged;
    private final int slugChanged;

    @Builder(access = AccessLevel.PRIVATE)
    private ProductAssignment(int primaryChanged, int slugChanged) {
        this.primaryChanged = primaryChanged;
        this.slugChanged = slugChanged;
    }

    public static ProductAssignment of(int primaryChanged, int slugChanged) {
        return ProductAssignment.builder().primaryChanged(primaryChanged).slugChanged(slugChanged).build();
    }
}
