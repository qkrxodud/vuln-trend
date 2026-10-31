package com.vulntrend.batch.domain.enrichment;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.List;
import java.util.Set;

/** 악용 확률(EPSS)을 취약점 목록에 반영하는 저장소 */
public interface EpssRepository {

    /** 취약점 목록에 있는 CVE 번호. EPSS 파일(약 40만 줄)에서 이것만 골라 반영한다 */
    Set<CveId> knownIds();

    /** 확률과 순위를 함께 바꾼다. 바꾼 CVE 수를 돌려준다. 이번 파일에 없는 CVE 의 값은 그대로 둔다 */
    int applyAll(List<EpssScore> scores);

    /** 취약점 목록의 전체 CVE 수. EPSS 가 얼마나 덮었는지 알리는 데 쓴다(WBS 2 DoD: 90% 이상) */
    int vulnerabilityCount();
}
