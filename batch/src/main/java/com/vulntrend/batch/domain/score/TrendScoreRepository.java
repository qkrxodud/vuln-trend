package com.vulntrend.batch.domain.score;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/** 트렌드 점수 저장소. 점수식은 저장소 안의 SQL 한 방이다(기획서 6장, WBS 3.1 — 자바 반복 금지) */
public interface TrendScoreRepository {

    /** 철회되지 않은 모든 CVE 의 점수를 now 기준으로 다시 매긴다. 매긴 CVE 수를 돌려준다 */
    int rescoreAll(Instant now);

    /** 물어본 CVE 의 항별 기여도. 목록에 없는 CVE 는 빠진다. 출력 단계(Phase 4)가 쓴다 */
    Map<CveId, ScoreBreakdown> breakdownsOf(Collection<CveId> ids, Instant now);
}
