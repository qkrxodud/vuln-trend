package com.vulntrend.batch.infrastructure.persistence.score;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 트렌드 목록의 순서와 대상(WBS 4.2, 6.3 과 공유). 사이트 상위 목록(index.json)과 요약 대상(상위 20건)이 같은 줄을 보도록
 * 두 쪽 쿼리가 이 문장을 그대로 쓴다. desc 는 Postgres 기본이 nulls first 라서 nulls last 를 꼭 붙인다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TrendOrder {

    /** 철회되지 않은 CVE 만 목록에 오른다 */
    public static final String LISTED = "coalesce(rejected, false) = false";
    /** 점수 높은 순 → 같으면 최근 발행 순 → 같으면 번호 순 */
    public static final String ORDER_BY = "order by trend_score desc nulls last, published_at desc nulls last, vuln_id";
}
