package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.product.AffectedProductName;
import com.vulntrend.batch.domain.product.PrimaryProductCandidate;
import com.vulntrend.batch.domain.product.ProductAssignmentRepository;
import com.vulntrend.batch.domain.product.ProductFamily;
import com.vulntrend.batch.domain.product.ProductMention;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 대표 제품·제품군·묶음 이름 저장소 흉내. 정해 둔 후보를 돌려주고, 바꾸라고 받은 것을 기억한다 */
public class FakeProductAssignmentRepository implements ProductAssignmentRepository {

    private final List<PrimaryProductCandidate> candidates;
    private final List<AffectedProductName> affected;
    private final Map<CveId, Optional<String>> primaryChanges = new LinkedHashMap<>();
    private final Map<ProductMention, Optional<String>> slugChanges = new LinkedHashMap<>();
    private final Map<CveId, Optional<ProductFamily>> familyChanges = new LinkedHashMap<>();

    public FakeProductAssignmentRepository(List<PrimaryProductCandidate> candidates, List<AffectedProductName> affected) {
        this.candidates = List.copyOf(candidates);
        this.affected = List.copyOf(affected);
    }

    @Override
    public List<PrimaryProductCandidate> findPrimaryCandidates() {
        return candidates;
    }

    @Override
    public int changePrimaryProducts(Map<CveId, Optional<String>> changes) {
        primaryChanges.putAll(changes);
        return changes.size();
    }

    @Override
    public int changeFamilies(Map<CveId, Optional<ProductFamily>> changes) {
        familyChanges.putAll(changes);
        return changes.size();
    }

    public Map<CveId, Optional<ProductFamily>> familyChanges() {
        return Map.copyOf(familyChanges);
    }

    @Override
    public List<AffectedProductName> findAffectedProductNames() {
        return affected;
    }

    @Override
    public int changeSlugs(Map<ProductMention, Optional<String>> changes) {
        slugChanges.putAll(changes);
        return changes.size();
    }

    public Map<CveId, Optional<String>> primaryChanges() {
        return Map.copyOf(primaryChanges);
    }

    public Map<ProductMention, Optional<String>> slugChanges() {
        return Map.copyOf(slugChanges);
    }
}
