package com.vulntrend.batch.domain.category;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** CVE 하나의 약점 번호 원문(NVD 원본)과 지금 저장된 공격 유형 키(vulnerability.attack_types). 매기기 단계가 비교에 쓴다 */
@Getter
public class CveWeaknesses {

    private final CveId cveId;
    private final List<String> cweIds;
    private final List<String> currentKeys;

    @Builder(access = AccessLevel.PRIVATE)
    private CveWeaknesses(CveId cveId, List<String> cweIds, List<String> currentKeys) {
        this.cveId = cveId;
        this.cweIds = cweIds;
        this.currentKeys = currentKeys;
    }

    public static CveWeaknesses of(CveId cveId, List<String> cweIds, List<String> currentKeys) {
        return CveWeaknesses.builder().cveId(cveId).cweIds(cweIds).currentKeys(currentKeys).build();
    }

    /** 약점 번호로 매긴 공격 유형(AttackTypes 규칙) */
    public List<AttackType> classify() {
        return AttackTypes.classify(cweIds);
    }

    /** 저장된 키가 types 와 같은 순서로 같은가(같으면 다시 쓰지 않는다) */
    public boolean hasAttackTypes(List<AttackType> types) {
        return currentKeys.equals(types.stream().map(AttackType::getKey).toList());
    }
}
