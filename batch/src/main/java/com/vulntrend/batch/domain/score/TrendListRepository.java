package com.vulntrend.batch.domain.score;

import com.vulntrend.batch.domain.score.dto.TrendListing;
import java.time.Instant;
import java.util.List;

/** 트렌드 목록 조회(WBS 4.2). 둘 다 철회 건을 빼고 트렌드 순서(점수 → 최근 발행 → 번호)로 돌려준다 */
public interface TrendListRepository {

    /** 점수 상위 limit 건 */
    List<TrendListing> top(int limit, Instant now);

    /** 발행 시각이 [from, to) 안인 건 전부, 최근 발행 순(같으면 번호 순). 점수 구성은 now 기준 */
    List<TrendListing> publishedBetween(Instant from, Instant to, Instant now);
}
