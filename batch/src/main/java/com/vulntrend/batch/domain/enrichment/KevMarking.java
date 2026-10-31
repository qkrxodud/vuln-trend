package com.vulntrend.batch.domain.enrichment;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** KEV 반영 결과. marked 는 실제 악용 표시를 붙인 CVE 수, missing 은 아직 취약점 목록에 없어 붙이지 못한 CVE */
@Getter
public class KevMarking {

    private final int marked;
    private final List<CveId> missing;

    @Builder(access = AccessLevel.PRIVATE)
    private KevMarking(int marked, List<CveId> missing) {
        this.marked = marked;
        this.missing = missing;
    }

    public static KevMarking of(int marked, List<CveId> missing) {
        return KevMarking.builder().marked(marked).missing(List.copyOf(missing)).build();
    }
}
