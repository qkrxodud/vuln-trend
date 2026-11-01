package com.vulntrend.batch.application.category;

import com.vulntrend.batch.domain.category.AttackType;
import com.vulntrend.batch.domain.category.AttackTypeAssignmentRepository;
import com.vulntrend.batch.domain.category.CveWeaknesses;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 공격 유형 매기기(사이트 분류, 사용자 결정 2026-11-01). 정기 실행마다 대표 제품 다음, 점수 단계 전에 돈다.
 * 모든 CVE 의 약점 번호를 표(AttackTypes)로 다시 매기고 달라진 것만 쓴다 — 표를 고치면 다음 실행에서 모두 새 표로 바뀐다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttackTypeAssignService {

    private final AttackTypeAssignmentRepository repository;

    public int assignAll() {
        Map<CveId, List<AttackType>> changes = new LinkedHashMap<>();
        for (CveWeaknesses weaknesses : repository.findWeaknesses()) {
            List<AttackType> types = weaknesses.classify();
            if (weaknesses.hasAttackTypes(types)) {
                continue;
            }
            changes.put(weaknesses.getCveId(), types);
        }
        int changed = repository.changeAttackTypes(changes);
        log.info("Attack types changed for {} CVEs.", changed);
        return changed;
    }
}
