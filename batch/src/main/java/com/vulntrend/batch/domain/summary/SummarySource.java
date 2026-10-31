package com.vulntrend.batch.domain.summary;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 요약할 CVE 한 건의 원문. Claude 에는 NVD 설명과 영향 제품·버전 범위·고친 버전만 넘긴다(기획서 6장 요약 가드).
 * CVE 번호는 저장용, CVSS 점수는 "원문에 없는 버전" 검사에서 점수(9.8)를 버전으로 오해하지 않게 쓰려고 들고 있다.
 */
@Getter
public class SummarySource {

    private final CveId cveId;
    private final String description;
    private final List<SummaryProduct> products;
    @Getter(AccessLevel.NONE)
    private final BigDecimal cvssScore;

    @Builder(access = AccessLevel.PRIVATE)
    private SummarySource(CveId cveId, String description, List<SummaryProduct> products, BigDecimal cvssScore) {
        this.cveId = cveId;
        this.description = description;
        this.products = products;
        this.cvssScore = cvssScore;
    }

    public static SummarySource of(CveId cveId, String description, List<SummaryProduct> products,
            BigDecimal cvssScore) {
        return SummarySource.builder().cveId(cveId).description(description).products(List.copyOf(products))
                .cvssScore(cvssScore).build();
    }

    public Optional<BigDecimal> getCvssScore() {
        return Optional.ofNullable(cvssScore);
    }

    /** 버전 숫자의 출처로 인정하는 글: 설명, 제품 이름, 영향 범위, 고친 버전(프론트 gate:check 와 같은 기준) */
    public String sourceText() {
        Stream<String> productTexts = products.stream().flatMap(p -> Stream.of(
                p.getProduct(), p.getVulnerableRange().orElse(""), p.getFixedVersion().orElse("")));
        return Stream.concat(Stream.of(description), productTexts).collect(Collectors.joining("\n"));
    }

    /** CVSS 점수를 글로 쓴 모양들(9.8, 10 과 10.0). 요약에 점수가 나와도 버전으로 보지 않는다 */
    public List<String> scoreTexts() {
        return getCvssScore()
                .map(score -> List.of(score.stripTrailingZeros().toPlainString(),
                        score.setScale(1, RoundingMode.HALF_UP).toPlainString()))
                .orElse(List.of());
    }
}
