package com.vulntrend.batch.domain.score;

import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 트렌드 목록 조회(WBS 4.2). 둘 다 철회 건을 빼고 트렌드 순서(점수 → 최근 발행 → 번호)로 돌려준다 */
public interface TrendListRepository {

    /** 점수 상위 limit 건 */
    List<TrendListing> top(int limit, Instant now);

    /** 제품군 키마다 그 제품군 안 트렌드 순서 상위 limit 건의 번호(순서대로). 한 건도 없는 제품군은 없다 */
    Map<String, List<CveId>> topIdsByFamily(int limit);

    /** 공격 유형 키마다 그 유형이 붙은 것 중 트렌드 순서 상위 limit 건의 번호(순서대로). 여러 유형이 붙은 CVE 는 유형마다 든다 */
    Map<String, List<CveId>> topIdsByAttackType(int limit);

    /** 그 번호들의 목록 줄(없거나 철회된 번호는 빠진다, 순서는 정하지 않음). 점수 구성은 now 기준 */
    List<TrendListing> listings(Collection<CveId> ids, Instant now);

    /** 발행 시각이 [from, to) 안인 건 전부, 최근 발행 순(같으면 번호 순). 점수 구성은 now 기준 */
    List<TrendListing> publishedBetween(Instant from, Instant to, Instant now);
}
