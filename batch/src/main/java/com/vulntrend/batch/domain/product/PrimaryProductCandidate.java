package com.vulntrend.batch.domain.product;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 대표 제품을 정할 재료 하나: CVE 번호, 지금 대표 제품, KEV 표기, NVD 의 영향받는 첫 CPE 항목. 뒤 셋은 없을 수 있다 */
public class PrimaryProductCandidate {

    @Getter
    private final CveId cveId;
    private final String currentPrimary;
    private final ProductMention kev;
    private final ProductMention firstCpe;

    @Builder(access = AccessLevel.PRIVATE)
    private PrimaryProductCandidate(CveId cveId, String currentPrimary, ProductMention kev, ProductMention firstCpe) {
        this.cveId = cveId;
        this.currentPrimary = currentPrimary;
        this.kev = kev;
        this.firstCpe = firstCpe;
    }

    public static PrimaryProductCandidate of(CveId cveId, String currentPrimary, ProductMention kev,
            ProductMention firstCpe) {
        return PrimaryProductCandidate.builder()
                .cveId(cveId)
                .currentPrimary(currentPrimary)
                .kev(kev)
                .firstCpe(firstCpe)
                .build();
    }

    public Optional<String> getCurrentPrimary() {
        return Optional.ofNullable(currentPrimary);
    }

    public Optional<ProductMention> getKev() {
        return Optional.ofNullable(kev);
    }

    public Optional<ProductMention> getFirstCpe() {
        return Optional.ofNullable(firstCpe);
    }
}
