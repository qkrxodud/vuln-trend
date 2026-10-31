package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.enrichment.KevEntry;
import com.vulntrend.batch.domain.enrichment.KevMarking;
import com.vulntrend.batch.domain.enrichment.KevRepository;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** 실제 악용 목록 반영 흉내. 취약점 목록에 있다고 정해 둔 CVE 만 표시한 것으로 치고, 받은 목록과 시각을 기억한다 */
public class FakeKevRepository implements KevRepository {

    private final Set<CveId> known;
    private final List<KevEntry> received = new ArrayList<>();
    private Instant fetchedAt;

    public FakeKevRepository(Set<CveId> known) {
        this.known = Set.copyOf(known);
    }

    @Override
    public KevMarking markAll(List<KevEntry> entries, Instant fetchedAt) {
        received.addAll(entries);
        this.fetchedAt = fetchedAt;
        List<CveId> missing = entries.stream().map(KevEntry::getCveId).filter(Predicate.not(known::contains)).toList();
        return KevMarking.of(entries.size() - missing.size(), missing);
    }

    public List<KevEntry> received() {
        return List.copyOf(received);
    }

    public Instant fetchedAt() {
        return fetchedAt;
    }
}
