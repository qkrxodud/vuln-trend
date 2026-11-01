package com.vulntrend.batch.domain.category;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.List;
import java.util.Map;

/**
 * 공격 유형 매기기 저장소(vulnerability.attack_types). 약점 번호는 수집기를 바꾸지 않고 저장된 NVD 원본에서 읽는다.
 * 구현은 infrastructure/persistence/category 의 JdbcAttackTypeAssignmentRepository.
 */
public interface AttackTypeAssignmentRepository {

    /** 모든 CVE 의 약점 번호 원문(없으면 빈 목록)과 지금 저장된 공격 유형 키 */
    List<CveWeaknesses> findWeaknesses();

    /** 바꾼 CVE 수 */
    int changeAttackTypes(Map<CveId, List<AttackType>> changes);
}
