package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.enrichment.EpssRepository;
import com.vulntrend.batch.domain.enrichment.EpssScore;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 악용 확률 반영 흉내. 취약점 목록에 있다고 정해 둔 CVE 를 알려 주고, 받은 점수와 반영한 점수 파일 날짜를 기억한다 */
public class FakeEpssRepository implements EpssRepository {

    private final Set<CveId> known;
    private final List<EpssScore> applied = new ArrayList<>();
    private LocalDate scoreDate;
    private Instant recordedAt;

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

    /** 전에 이 날짜의 점수 파일을 반영했다고 해 둔다 */
    public FakeEpssRepository appliedOn(LocalDate date) {
        this.scoreDate = date;
        return this;
    }

    @Override
    public Optional<LocalDate> appliedScoreDate() {
        return Optional.ofNullable(scoreDate);
    }

    @Override
    public void recordScoreDate(LocalDate date, Instant appliedAt) {
        this.scoreDate = date;
        this.recordedAt = appliedAt;
    }

    public Optional<Instant> recordedAt() {
        return Optional.ofNullable(recordedAt);
    }

    @Override
    public int vulnerabilityCount() {
        return known.size();
    }

    public List<EpssScore> applied() {
        return List.copyOf(applied);
    }
}
