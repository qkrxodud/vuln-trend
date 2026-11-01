package com.vulntrend.batch.domain.product;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 대표 제품·제품군을 정할 재료 하나: CVE 번호, 지금 대표 제품, KEV 표기, NVD 의 영향받는 첫 CPE 항목(이름과 CPE 2.3 원문),
 * NVD 발행 기관(CNA), 지금 제품군. CVE 번호 말고는 없을 수 있다
 */
public class PrimaryProductCandidate {

    @Getter
    private final CveId cveId;
    private final String currentPrimary;
    private final ProductMention kev;
    private final ProductMention firstCpe;
    private final CpeCriteria firstCpeCriteria;
    @Getter
    private final String cna;
    private final ProductFamily currentFamily;

    @Builder(access = AccessLevel.PRIVATE)
    private PrimaryProductCandidate(CveId cveId, String currentPrimary, ProductMention kev, ProductMention firstCpe,
            CpeCriteria firstCpeCriteria, String cna, ProductFamily currentFamily) {
        this.cveId = cveId;
        this.currentPrimary = currentPrimary;
        this.kev = kev;
        this.firstCpe = firstCpe;
        this.firstCpeCriteria = firstCpeCriteria;
        this.cna = cna;
        this.currentFamily = currentFamily;
    }

    /** 제품군 근거(CPE 원문·CNA)와 지금 제품군이 없는 재료 */
    public static PrimaryProductCandidate of(CveId cveId, String currentPrimary, ProductMention kev,
            ProductMention firstCpe) {
        return of(cveId, currentPrimary, kev, firstCpe, null, null, null);
    }

    public static PrimaryProductCandidate of(CveId cveId, String currentPrimary, ProductMention kev,
            ProductMention firstCpe, CpeCriteria firstCpeCriteria, String cna, ProductFamily currentFamily) {
        return PrimaryProductCandidate.builder()
                .cveId(cveId)
                .currentPrimary(currentPrimary)
                .kev(kev)
                .firstCpe(firstCpe)
                .firstCpeCriteria(firstCpeCriteria)
                .cna(cna)
                .currentFamily(currentFamily)
                .build();
    }

    public Optional<CpeCriteria> getFirstCpeCriteria() {
        return Optional.ofNullable(firstCpeCriteria);
    }

    public Optional<ProductFamily> getCurrentFamily() {
        return Optional.ofNullable(currentFamily);
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
