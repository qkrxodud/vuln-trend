package com.vulntrend.batch.domain.summary;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 요약에 넘기는 영향 제품 한 줄. 범위가 비어 있으면 모든 버전, 고친 버전은 OSV 수집 뒤에만 채워진다 */
@Getter
public class SummaryProduct {

    private final String vendor;
    private final String product;
    @Getter(AccessLevel.NONE)
    private final String vulnerableRange;
    @Getter(AccessLevel.NONE)
    private final String fixedVersion;

    @Builder(access = AccessLevel.PRIVATE)
    private SummaryProduct(String vendor, String product, String vulnerableRange, String fixedVersion) {
        this.vendor = vendor;
        this.product = product;
        this.vulnerableRange = vulnerableRange;
        this.fixedVersion = fixedVersion;
    }

    public static SummaryProduct of(String vendor, String product, String vulnerableRange, String fixedVersion) {
        return SummaryProduct.builder().vendor(vendor).product(product).vulnerableRange(vulnerableRange)
                .fixedVersion(fixedVersion).build();
    }

    public Optional<String> getVulnerableRange() {
        return Optional.ofNullable(vulnerableRange);
    }

    public Optional<String> getFixedVersion() {
        return Optional.ofNullable(fixedVersion);
    }
}
