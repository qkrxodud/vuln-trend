package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.category.AttackType;
import com.vulntrend.batch.domain.category.AttackTypeAssignmentRepository;
import com.vulntrend.batch.domain.category.CveWeaknesses;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 공격 유형 매기기 저장소 흉내. 넣어 둔 약점·지금 유형을 돌려주고, 바꾸라고 받은 것을 기억해 다음 조회에 반영한다 */
public class FakeAttackTypeAssignmentRepository implements AttackTypeAssignmentRepository {

    private final Map<CveId, List<String>> cwes = new LinkedHashMap<>();
    private final Map<CveId, List<String>> keys = new LinkedHashMap<>();
    private final List<Map<CveId, List<AttackType>>> changeCalls = new ArrayList<>();

    public FakeAttackTypeAssignmentRepository with(CveId id, List<String> cweIds, List<String> currentKeys) {
        cwes.put(id, cweIds);
        keys.put(id, currentKeys);
        return this;
    }

    @Override
    public List<CveWeaknesses> findWeaknesses() {
        return cwes.keySet().stream().map(id -> CveWeaknesses.of(id, cwes.get(id), keys.get(id))).toList();
    }

    @Override
    public int changeAttackTypes(Map<CveId, List<AttackType>> changes) {
        changeCalls.add(Map.copyOf(changes));
        changes.forEach((id, types) -> keys.put(id, types.stream().map(AttackType::getKey).toList()));
        return changes.size();
    }

    public List<String> keysOf(CveId id) {
        return keys.get(id);
    }

    /** 마지막으로 바꾸라고 받은 것(한 번도 안 받았으면 빈 맵) */
    public Map<CveId, List<AttackType>> lastChanges() {
        return changeCalls.isEmpty() ? Map.of() : changeCalls.get(changeCalls.size() - 1);
    }
}
