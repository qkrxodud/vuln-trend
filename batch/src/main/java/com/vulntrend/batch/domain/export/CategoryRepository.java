package com.vulntrend.batch.domain.export;

import com.vulntrend.batch.domain.export.dto.CategoryCount;
import java.time.Instant;
import java.util.Map;

/**
 * 분류별 건수(categories.json, 사용자 결정 2026-11-01). 철회 건은 세지 않는다. 키는 제품군·공격 유형의 키이고,
 * 한 건도 없는 분류는 맵에 없다
 */
public interface CategoryRepository {

    /** 제품군별: [weekFrom, to)·[monthFrom, to) 에 발행된 수와 KEV 전체 수 */
    Map<String, CategoryCount> familyCounts(Instant weekFrom, Instant monthFrom, Instant to);

    /** 공격 유형별(한 CVE 가 여러 유형에 들어갈 수 있다): 같은 세 가지 수 */
    Map<String, CategoryCount> attackTypeCounts(Instant weekFrom, Instant monthFrom, Instant to);
}
