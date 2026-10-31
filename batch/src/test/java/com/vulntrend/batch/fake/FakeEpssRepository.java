package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.enrichment.EpssRepository;
import com.vulntrend.batch.domain.enrichment.EpssScore;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 악용 확률 반영 흉내. 취약점 목록에 있다고 정해 둔 CVE 를 알려 주고, 받은 점수를 기억한다 */
public class FakeEpssRepository implements EpssRepository {

    private final Set<CveId> known;
    private final List<EpssScore> applied = new ArrayList<>();

    public FakeEpssRepository(Set<CveId> known) {
        this.known = Set.copyOf(known);
    }

    @Override
    public Set<CveId> knownIds() {
        return known;
    }

    @Override
    public int applyAll(List<EpssScore> scores) {
        applied.addAll(scores);
        return scores.size();
    }

    @Override
    public int vulnerabilityCount() {
        return known.size();
    }

    public List<EpssScore> applied() {
        return List.copyOf(applied);
    }
}
