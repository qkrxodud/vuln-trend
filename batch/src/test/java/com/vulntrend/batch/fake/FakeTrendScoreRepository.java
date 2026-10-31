package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.score.ScoreBreakdown;
import com.vulntrend.batch.domain.score.TrendScoreRepository;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/** 트렌드 점수 저장소 흉내. 점수를 다시 매긴 시각을 기억하고 정해 둔 수를 돌려준다 */
public class FakeTrendScoreRepository implements TrendScoreRepository {

    private final int rows;
    private Instant rescoredAt;

    public FakeTrendScoreRepository(int rows) {
        this.rows = rows;
    }

    @Override
    public int rescoreAll(Instant now) {
        this.rescoredAt = now;
        return rows;
    }

    @Override
    public Map<CveId, ScoreBreakdown> breakdownsOf(Collection<CveId> ids, Instant now) {
        return Map.of();
    }

    public Instant rescoredAt() {
        return rescoredAt;
    }
}
